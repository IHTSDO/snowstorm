package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Entity;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.commons.lang3.math.NumberUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.QueryConceptRepository;
import org.snomed.snowstorm.core.data.services.pojo.SAxiomRepresentation;
import org.snomed.snowstorm.core.data.services.transitiveclosure.GraphBuilder;
import org.snomed.snowstorm.core.data.services.transitiveclosure.GraphBuilderException;
import org.snomed.snowstorm.core.data.services.transitiveclosure.Node;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.mrcm.MRCMLoader;
import org.snomed.snowstorm.mrcm.model.AttributeRange;
import org.snomed.snowstorm.mrcm.model.MRCM;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.CONCEPT_MODEL_OBJECT_ATTRIBUTE;

@Service
public class SemanticIndexUpdateService extends ComponentService implements CommitListener {

	private static final long CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG = parseLong(CONCEPT_MODEL_OBJECT_ATTRIBUTE);
	private static final long CONCEPT_MODEL_ATTRIBUTE_LONG = parseLong(Concepts.CONCEPT_MODEL_ATTRIBUTE);

	@Value("${commit-hook.semantic-indexing.enabled:true}")
	private boolean semanticIndexingEnabled;

	private static final long IS_A_TYPE = parseLong(Concepts.ISA);

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private QueryConceptRepository queryConceptRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private AxiomConversionService axiomConversionService;

	@Autowired
	private MRCMLoader mrcmLoader;

	private final Logger logger = LoggerFactory.getLogger(getClass());


	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (semanticIndexingEnabled) {
			try {
				updateStatedAndInferredSemanticIndex(commit);
			} catch (ConversionException | GraphBuilderException | ServiceException e) {
				throw new IllegalStateException("Failed to update semantic index. " + e.getMessage(), e);
			}
		} else {
			logger.info("Semantic indexing is disabled.");
		}
	}

	public Map<String, Integer> rebuildStatedAndInferredSemanticIndex(String branchPath, boolean dryRun) throws ServiceException {
		try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata("Rebuilding semantic index."))) {
			final Map<String, Integer> updateCounts = rebuildSemanticIndex(commit, dryRun);
			if (!dryRun && updateCounts.values().stream().anyMatch(updateCount -> updateCount > 0)) {
				commit.markSuccessful();
			} else {
				logger.info("{} so rolling back the empty commit on {}.", dryRun ? "Dry run mode" : "No semantic changes required", branchPath);
				// When a commit is not marked as successful it is rolled back automatically when closed.
			}
			return updateCounts;
		} catch (ConversionException | GraphBuilderException e) {
			throw new ServiceException("Failed to update semantic index. " + e.getMessage(), e);
		}
	}

	private void updateStatedAndInferredSemanticIndex(Commit commit) throws IllegalStateException, ConversionException, GraphBuilderException, ServiceException {
		if (commit.isRebase()) {
			rebuildSemanticIndex(commit, false);
		} else if (commit.getCommitType() != Commit.CommitType.PROMOTION) {
			// Update query index using changes in the current commit

			// Process deletions from this commit only
			Set<String> relationshipAndAxiomDeletionsToProcess =
					Sets.union(commit.getEntityVersionsReplaced().getOrDefault(ReferenceSetMember.class.getSimpleName(), Collections.emptySet()),
							commit.getEntityVersionsReplaced().getOrDefault(Relationship.class.getSimpleName(), Collections.emptySet()));

			updateSemanticIndex(Form.STATED, relationshipAndAxiomDeletionsToProcess, commit, false, false, false);
			updateSemanticIndex(Form.INFERRED, relationshipAndAxiomDeletionsToProcess, commit, false, false, false);
		}
		// If promotion the semantic changes will be promoted with the rest of the content.
	}

	private Map<String, Integer> rebuildSemanticIndex(Commit commit, boolean dryRun) throws ConversionException, GraphBuilderException, ServiceException {
		Branch branch = commit.getBranch();

		Set<String> relationshipAndAxiomDeletionsToProcess = Sets.union(branch.getVersionsReplaced(ReferenceSetMember.class), branch.getVersionsReplaced(Relationship.class));
		boolean completeRebuild = branch.getPath().equals("MAIN");
		if (!completeRebuild) {
			// Recreate query index using new parent base point + content on this branch
			if (dryRun) {
				throw new IllegalArgumentException("dryRun flag can only be used when rebuilding the index of the MAIN branch.");
			}
			removeQConceptChangesOnBranch(commit);
		}
		Map<String, Integer> updateCount = new HashMap<>();
		updateCount.put(Form.STATED.getName(), updateSemanticIndex(Form.STATED, relationshipAndAxiomDeletionsToProcess, commit, true, completeRebuild, dryRun));
		updateCount.put(Form.INFERRED.getName(), updateSemanticIndex(Form.INFERRED, relationshipAndAxiomDeletionsToProcess, commit, true, completeRebuild, dryRun));
		return updateCount;
	}

	private int updateSemanticIndex(Form form, Set<String> internalIdsOfDeletedComponents, Commit commit,
			boolean rebuild, boolean completeRebuild, boolean dryRun) throws IllegalStateException, ConversionException, GraphBuilderException, ServiceException {

		if (dryRun && !completeRebuild) {
			throw new IllegalArgumentException("dryRun flag can only be used when rebuilding the index of the MAIN branch.");
		}

		// Note: Searches within this method use a filter clause for collections of identifiers because these
		//       can become larger than the maximum permitted query criteria.

		TimerUtil timer = new TimerUtil("TC index " + form.getName(), Level.INFO, 1);
		final Branch branch = commit.getBranch();
		String branchPath = branch.getPath();

		BranchCriteria previousStateCriteria;
		BranchCriteria changesCriteria;
		BranchCriteria newStateCriteria;
		if (rebuild) {
			if (completeRebuild) {
				// Not used until the end
				previousStateCriteria = versionControlHelper.getBranchCriteria(branch);
				// Force everything
				newStateCriteria = previousStateCriteria;
				// Not used in complete rebuild
				changesCriteria = null;
			} else {
				// Take existing content from parent branch
				previousStateCriteria = versionControlHelper.getBranchCriteriaAtTimepoint(PathUtil.getParentPath(branchPath), branch.getBase());
				// Standard selection on already committed content. Including open commit to include manually resolved conflicts.
				newStateCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
				// Process all changes on branch
				changesCriteria = versionControlHelper.getChangesOnBranchCriteria(branch);
			}
		} else {
			// Take existing content from this branch before the current commit
			previousStateCriteria = versionControlHelper.getBranchCriteriaBeforeOpenCommit(commit);
			// Current commit may contain content
			newStateCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
			// Process changes in this commit
			changesCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
		}
		timer.checkpoint("get branch criteria");

		// Identify concepts with modeling changes  and load relevant parts of the existing node graph
		final GraphBuilder graphBuilder = new GraphBuilder();
		Set<Long> updatedConceptIds;
		boolean newGraph;
		if (completeRebuild) {
			updatedConceptIds = Collections.emptySet();
			newGraph = true;
			logger.info("Performing rebuild of {} semantic index", form.getName());
		} else {
			updatedConceptIds = buildRelevantPartsOfExistingGraph(graphBuilder, form, changesCriteria, previousStateCriteria, internalIdsOfDeletedComponents, timer);
			if (updatedConceptIds.isEmpty()) {
				// Nothing to do
				return 0;
			}
			// Strategy: Clear the modelling of updated concepts then add/remove edges and attributes based on the new commit
			newGraph = graphBuilder.getNodeCount() == 0;
			// Clear parents of updated concepts
			for (Long updatedConceptId : updatedConceptIds) {
				graphBuilder.clearParentsAndMarkUpdated(updatedConceptId);
			}
		}

		// Step - Update graph in memory
		Set<Long> requiredActiveConcepts = new LongOpenHashSet();
		Map<Long, AttributeChanges> conceptAttributeChanges = new Long2ObjectOpenHashMap<>();

		final Map<String, ConcreteValue.DataType> concreteAttributeDataTypeMap = getConcreteAttributeDataTypeMap(commit);
		// Create consumer for relationship changes
		BiConsumer<SnomedComponent<?>, Relationship> relationshipConsumer = (component, relationship) -> {
			long conceptId = parseLong(relationship.getSourceId());
			int groupId = relationship.getGroupId();
			long type = parseLong(relationship.getTypeId());
			Integer effectiveTime = component.getEffectiveTimeI();
			if (relationship.isConcrete()) {
				conceptAttributeChanges.computeIfAbsent(conceptId, c -> new AttributeChanges())
						.addAttribute(effectiveTime, groupId, type, convertConcreteValue(relationship, concreteAttributeDataTypeMap));
			} else {
				// use destination concepts
				long destinationId = parseLong(relationship.getDestinationId());
				requiredActiveConcepts.add(destinationId);
				if (type == IS_A_TYPE) {
					graphBuilder.addParent(conceptId, destinationId);
					// Concept model object attribute is not linked to the concept hierarchy by any axiom
					// however we want the link in the semantic index so let's add it here.
					if (CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG == destinationId) {
						graphBuilder.addParent(CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG, CONCEPT_MODEL_ATTRIBUTE_LONG);
					}
				} else {
					// Destination concept id is stored as String in the semantic index
					conceptAttributeChanges.computeIfAbsent(conceptId, c -> new AttributeChanges()).addAttribute(effectiveTime, groupId, type, String.valueOf(destinationId));
				}
			}
			requiredActiveConcepts.add(conceptId);
			requiredActiveConcepts.add(type);
		};

		final BoolQueryBuilder sourceFilter = completeRebuild ? boolQuery() : boolQuery().must(termsQuery(Relationship.Fields.SOURCE_ID, updatedConceptIds));
		try (final SearchHitsIterator<Relationship> activeRelationships = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(newStateCriteria.getEntityBranchCriteria(Relationship.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
						.must(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, form.getCharacteristicTypeIds()))
						.filter(sourceFilter)
				)
				.withSort(SortBuilders.fieldSort(SnomedComponent.Fields.EFFECTIVE_TIME))
				.withSort(SortBuilders.fieldSort(SnomedComponent.Fields.ACTIVE))
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(LARGE_PAGE).build(), Relationship.class)) {
			activeRelationships.forEachRemaining(hit -> relationshipConsumer.accept(hit.getContent(), hit.getContent()));
		}
		timer.checkpoint("Update graph using relationships of concepts with changed modelling.");

		if (form.isStated()) {
			final BoolQueryBuilder referencedComponentFilter = boolQuery();
			final NativeSearchQueryBuilder axiomSearchBuilder = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(newStateCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
							.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
							.filter(referencedComponentFilter)
					)
					.withSort(SortBuilders.fieldSort(SnomedComponent.Fields.EFFECTIVE_TIME))
					.withSort(SortBuilders.fieldSort(SnomedComponent.Fields.ACTIVE))
					.withSort(SortBuilders.fieldSort("start"))
					.withPageable(LARGE_PAGE);
			if (completeRebuild) {
				try (final SearchHitsIterator<ReferenceSetMember> activeAxioms = elasticsearchTemplate.searchForStream(axiomSearchBuilder.build(), ReferenceSetMember.class)) {
					axiomStreamToRelationshipStream(activeAxioms, relationship -> true, relationshipConsumer);
				}
			} else {
				for (List<Long> batch : Iterables.partition(updatedConceptIds, CLAUSE_LIMIT)) {
					referencedComponentFilter.must().clear();
					referencedComponentFilter.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, batch));
					try (final SearchHitsIterator<ReferenceSetMember> activeAxioms = elasticsearchTemplate.searchForStream(axiomSearchBuilder.build(), ReferenceSetMember.class)) {
						axiomStreamToRelationshipStream(activeAxioms, relationship -> true, relationshipConsumer);
					}
				}
			}
			timer.checkpoint("Update graph using axioms of concepts with changed modelling.");
		}

		Set<Long> inactiveOrMissingConceptIds = new LongOpenHashSet();
		for (List<Long> batch : Iterables.partition(requiredActiveConcepts, CLAUSE_LIMIT)) {
			inactiveOrMissingConceptIds.addAll(getInactiveOrMissingConceptIds(Sets.newHashSet(batch), newStateCriteria));
		}
		if (!inactiveOrMissingConceptIds.isEmpty()) {
			logger.warn("The following concepts have been referred to in relationships but are missing or inactive: {}", inactiveOrMissingConceptIds);
		}

		// Step: Save changes
		Map<Long, Node> nodesToSave = new Long2ObjectOpenHashMap<>();
		graphBuilder.getNodes().stream()
				.filter(node -> newGraph || node.isAncestorOrSelfUpdated() || conceptAttributeChanges.containsKey(node.getId()))
				.forEach(node -> nodesToSave.put(node.getId(), node));
		Set<Long> nodesNotFound = new LongOpenHashSet(nodesToSave.keySet());
		Set<QueryConcept> queryConceptsToSave = new HashSet<>();

		// Collect ids of nodes and attribute updates and convert to conceptIdForm
		Set<Long> conceptIdsToUpdate = new LongOpenHashSet(nodesToSave.keySet());
		conceptIdsToUpdate.addAll(conceptAttributeChanges.keySet());

		// If there is a loop found in the transitive closure we throw an exception,
		// unless it's a rebase/extension upgrade; that must be fixed manually afterwards
		// either by authoring or importing the new version of the extension.
		boolean throwExceptionIfTransitiveClosureLoopFound = !commit.isRebase();

		final BoolQueryBuilder filter = boolQuery()
				// Exclude those QueryConcepts which were removed in this commit
				.mustNot(boolQuery()
						.must(termQuery("path", branchPath))
						.must(termQuery("end", commit.getTimepoint().getTime()))
				);
		if (!completeRebuild) {
			filter.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIdsToUpdate));
		}
		try (final SearchHitsIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(previousStateCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.STATED, form.isStated()))
						.filter(filter)
				)
				.withPageable(LARGE_PAGE).build(), QueryConcept.class)) {
			while (existingQueryConcepts.hasNext()) {
				QueryConcept queryConcept = existingQueryConcepts.next().getContent();
				Long conceptId = queryConcept.getConceptIdL();
				Node node = nodesToSave.get(conceptId);
				boolean save = false;
				if (completeRebuild) {
					if (node != null) {
						QueryConcept newQueryConcept = createQueryConcept(form, branchPath, conceptAttributeChanges, throwExceptionIfTransitiveClosureLoopFound, node.getId(), node);
						if (!queryConcept.fieldsMatch(newQueryConcept)) {
							queryConcept = newQueryConcept;
							save = true;
						}
					} else {
						queryConcept.markDeleted();
						save = true;
					}
				} else {
					if (node != null) {
						// TC changes
						queryConcept.setParents(node.getParents().stream().map(Node::getId).collect(Collectors.toSet()));
						queryConcept.setAncestors(new HashSet<>(node.getTransitiveClosure(branchPath, throwExceptionIfTransitiveClosureLoopFound)));
						save = true;
					}
					if (updatedConceptIds.contains(conceptId)) {
						applyAttributeChanges(queryConcept, conceptId, conceptAttributeChanges);
						save = true;
					}
				}
				if (save) {
					queryConceptsToSave.add(queryConcept);
				}
				nodesNotFound.remove(conceptId);
			}
		}

		timer.checkpoint("Collect existingDescendants from QueryConcept.");

		// The remaining nodes are new - create new QueryConcepts
		for (Long nodeId : nodesNotFound) {
			Node node = nodesToSave.get(nodeId);
			QueryConcept queryConcept = createQueryConcept(form, branchPath, conceptAttributeChanges, throwExceptionIfTransitiveClosureLoopFound, nodeId, node);
			queryConcept.setCreating(true);
			queryConceptsToSave.add(queryConcept);
		}

		// Delete query concepts which have no parents
		queryConceptsToSave.stream().filter(c -> c.getParents().isEmpty() && !c.getConceptIdL().toString().equals(Concepts.SNOMEDCT_ROOT)).forEach(Entity::markDeleted);

		queryConceptsToSave.forEach(QueryConcept::serializeGroupedAttributesMap);

		final long countToCreate = queryConceptsToSave.stream().filter(QueryConcept::isCreating).count();
		final Optional<QueryConcept> firstToCreate = queryConceptsToSave.stream().filter(QueryConcept::isCreating).findFirst();
		String createMessage = firstToCreate.isPresent() ? String.format("%s semantic concepts created including %s.", countToCreate, firstToCreate.get()) :
				"No semantic concepts need creating.";

		final long countToUpdate = queryConceptsToSave.stream().filter(q -> q.isChanged() && !q.isCreating()).count();
		final Optional<QueryConcept> firstToUpdate = queryConceptsToSave.stream().filter(q -> q.isChanged() && !q.isCreating() && !q.isDeleted()).findFirst();
		String updateMessage = firstToUpdate.isPresent() ? String.format("%s semantic concepts updated including %s.", countToUpdate, firstToUpdate.get()) :
				"No semantic concepts need updating.";

		final long countToDelete = queryConceptsToSave.stream().filter(QueryConcept::isDeleted).count();
		final Optional<QueryConcept> firstToDelete = queryConceptsToSave.stream().filter(QueryConcept::isDeleted).findFirst();
		String deleteMessage = firstToDelete.isPresent() ? String.format("%s semantic concepts deleted including %s.", countToDelete, firstToDelete.get()) :
				"No semantic concepts need deleting.";

		logger.info("Semantic index change summary for {} form: {} concepts loaded into the graph. {} {} {}", form.getName(), graphBuilder.getNodes().size(),
				createMessage, updateMessage, deleteMessage);

		if (!queryConceptsToSave.isEmpty()) {

			if (dryRun) {
				logger.info("Semantic index rebuild is in dryRun mode so no changes will be persisted!");
			} else {
				// Save in batches
				for (List<QueryConcept> queryConcepts : Iterables.partition(queryConceptsToSave, Config.BATCH_SAVE_SIZE)) {
					doSaveBatch(queryConcepts, commit);
				}
			}
		}
		timer.checkpoint("Save updated QueryConcepts");
		logger.debug("{} concepts updated within the {} semantic index.", queryConceptsToSave.size(), form.getName());

		timer.finish();
		return queryConceptsToSave.size();
	}

	private QueryConcept createQueryConcept(Form form, String branchPath, Map<Long, AttributeChanges> conceptAttributeChanges,
			boolean throwExceptionIfTransitiveClosureLoopFound, Long nodeId, Node node) throws GraphBuilderException {

		final Set<Long> transitiveClosure = new HashSet<>(node.getTransitiveClosure(branchPath, throwExceptionIfTransitiveClosureLoopFound));
		final Set<Long> parentIds = node.getParents().stream().map(Node::getId).collect(Collectors.toSet());
		QueryConcept queryConcept = new QueryConcept(nodeId, parentIds, transitiveClosure, form.isStated());
		applyAttributeChanges(queryConcept, nodeId, conceptAttributeChanges);
		return queryConcept;
	}

	private Object convertConcreteValue(Relationship relationship, Map<String, ConcreteValue.DataType> concreteAttributeDataTypeMap) {
		ConcreteValue.DataType dataTypeDefined = concreteAttributeDataTypeMap.get(relationship.getTypeId());
		if (dataTypeDefined == null) {
			throw new IllegalStateException(String.format("No MRCM range constraint is defined for concrete attribute %s", relationship.getTypeId()));
		}
		ConcreteValue.DataType actualType = relationship.getConcreteValue().getDataType();
		String errorMsg = String.format("Concrete value %s with data type %s in relationship is not matching data type %s defined in the MRCM for attribute %s",
				relationship.getConcreteValue().getValue(), actualType, dataTypeDefined, relationship.getTypeId());

		if ((ConcreteValue.DataType.DECIMAL == dataTypeDefined && actualType == ConcreteValue.DataType.STRING) ||
				(ConcreteValue.DataType.STRING == dataTypeDefined && dataTypeDefined != actualType)) {
			throw new IllegalStateException(errorMsg);
		}

		// need to check the actual value for Integer data type
		if (ConcreteValue.DataType.INTEGER == dataTypeDefined) {
			int intValue = NumberUtils.toInt(relationship.getConcreteValue().getValue(), -1);
			if (intValue == -1) {
				throw new IllegalStateException(errorMsg);
			}
		}
		// convert concrete value
		Object value = relationship.getValue();
		if (ConcreteValue.DataType.INTEGER == dataTypeDefined) {
			value = Integer.parseInt(relationship.getConcreteValue().getValue());
		} else if (ConcreteValue.DataType.DECIMAL == dataTypeDefined) {
			value = Float.parseFloat(relationship.getConcreteValue().getValue());
		}
		return value;
	}

	private Map<String, ConcreteValue.DataType> getConcreteAttributeDataTypeMap(Commit commit) throws ServiceException {
		MRCM mrcm = mrcmLoader.loadActiveMRCM(commit.getBranch().getPath(), versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit));
		return mrcm.getAttributeRanges().stream().filter(r -> r.getDataType() != null)
				.collect(Collectors.toMap(AttributeRange::getReferencedComponentId, AttributeRange::getDataType, (r1, r2) -> r2));
	}

	private Set<Long> buildRelevantPartsOfExistingGraph(GraphBuilder graphBuilder, Form form,
			BranchCriteria changesCriteria, BranchCriteria existingContentCriteria,
			Set<String> internalIdsOfDeletedComponents, TimerUtil timer) throws ConversionException {

		Set<Long> updateSource = new LongOpenHashSet();
		Set<Long> updateDestination = new LongOpenHashSet();
		Set<Long> existingAncestors = new LongOpenHashSet();
		Set<Long> existingDescendants = new LongOpenHashSet();

		// Step: Collect source and destinations of changed is-a relationships
		try (final SearchHitsIterator<Relationship> changedIsARelationships = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery().filter(
								boolQuery()
										.must(termQuery("typeId", Concepts.ISA))
										.must(termsQuery("characteristicTypeId", form.getCharacteristicTypeIds()))
										.must(boolQuery()
												// Either on this branch
												.should(changesCriteria.getEntityBranchCriteria(Relationship.class))
												// Or on parent branch and deleted/replaced on this branch
												.should(idsQuery().addIds(internalIdsOfDeletedComponents.toArray(new String[]{})))
										)
						)
				)
				.withFields(Relationship.Fields.SOURCE_ID, Relationship.Fields.DESTINATION_ID)
				.withPageable(LARGE_PAGE).build(), Relationship.class)) {
			changedIsARelationships.forEachRemaining(hit -> {
				updateSource.add(parseLong(hit.getContent().getSourceId()));
				updateDestination.add(parseLong(hit.getContent().getDestinationId()));
			});
		}
		timer.checkpoint("Collect changed is-a relationships.");

		if (form.isStated()) {
			// Step: Collect source and destinations of is-a fragments within changed axioms
			try (final SearchHitsIterator<ReferenceSetMember> changedAxioms = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery().filter(
									boolQuery()
											.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
											.must(boolQuery()
													// Either on this branch
													.should(changesCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
													// Or on parent branch and deleted/replaced on this branch
													.should(termsQuery("internalId", internalIdsOfDeletedComponents))
											)
							)
					)
					.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_FIELD_PATH)
					.withPageable(LARGE_PAGE).build(), ReferenceSetMember.class)) {
				axiomStreamToRelationshipStream(
						changedAxioms,
						// filter
						relationship -> true,
						// for each
						(component, relationship) -> {
							updateSource.add(parseLong(relationship.getSourceId()));
							if (!relationship.isConcrete() && relationship.getTypeId().equals(Concepts.ISA)) {
								updateDestination.add(parseLong(relationship.getDestinationId()));
							}
						});
			}
			if (updateDestination.contains(CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG)) {
				updateDestination.add(CONCEPT_MODEL_ATTRIBUTE_LONG);
			}
			timer.checkpoint("Collect changed axiom is-a fragments.");
		}

		// Collect source of any other changed relationships
		try (SearchHitsIterator<Relationship> otherChangedRelationships = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery().filter(
								boolQuery()
										// Not 'is a'
										.mustNot(termQuery("typeId", Concepts.ISA))
										.must(termsQuery("characteristicTypeId", form.getCharacteristicTypeIds()))
										.must(boolQuery()
												// Either on this branch
												.should(changesCriteria.getEntityBranchCriteria(Relationship.class))
												// Or on parent branch and deleted/replaced on this branch
												.should(termsQuery("internalId", internalIdsOfDeletedComponents))
										)
										// Skip concepts already in the list
										.mustNot(termsQuery(Relationship.Fields.SOURCE_ID, updateSource))
						)
				)
				.withFields(Relationship.Fields.SOURCE_ID)
				.withPageable(LARGE_PAGE)
				.build(), Relationship.class)) {
			otherChangedRelationships.forEachRemaining(hit -> updateSource.add(parseLong(hit.getContent().getSourceId())));
		}

		if (updateSource.isEmpty()) {
			// Stop here - nothing to update
			return updateSource;
		}

		logger.info("Performing incremental update of {} semantic index", form.getName());

		// Identify parts of the graph that nodes are moving from or to

		// Step: Identify existing TC of updated nodes
		// Strategy: Find existing nodes where ID matches updated relationship source or destination ids, record TC
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(existingContentCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery("stated", form.isStated()))
						.filter(termsQuery(QueryConcept.Fields.CONCEPT_ID, Sets.union(updateSource, updateDestination)))
				)
				.withFields(QueryConcept.Fields.ANCESTORS)
				.withPageable(LARGE_PAGE).build();
		try (final SearchHitsIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.searchForStream(query, QueryConcept.class)) {
			existingQueryConcepts.forEachRemaining(hit -> existingAncestors.addAll(hit.getContent().getAncestors()));
		}
		timer.checkpoint("Collect existingAncestors from QueryConcept.");

		// Step: Identify existing descendants
		// Strategy: Find existing nodes where TC matches updated relationship source ids
		try (final SearchHitsIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(existingContentCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery("stated", form.isStated()))
						.filter(termsQuery("ancestors", updateSource))
				)
				.withFields(QueryConcept.Fields.CONCEPT_ID)
				.withPageable(LARGE_PAGE).build(), QueryConcept.class)) {
			existingQueryConcepts.forEachRemaining(hit -> existingDescendants.add(hit.getContent().getConceptIdL()));
		}
		timer.checkpoint("Collect existingDescendants from QueryConcept.");

		logger.debug("{} existing ancestors and {} existing descendants of updated relationships identified.", existingAncestors.size(), existingDescendants.size());

		// Step: Build existing graph
		// Iterative update.
		// Strategy: Load selection of existing nodes and use parents to build graph.
		Set<Long> nodesToLoad = new LongOpenHashSet();
		nodesToLoad.addAll(existingAncestors);
		nodesToLoad.addAll(existingDescendants);
		nodesToLoad.addAll(updateSource);
		nodesToLoad.addAll(updateDestination);

		// Build graph, collecting any alternative ancestors which have been missed.
		Set<Long> alternativeAncestors = new LongOpenHashSet();
		buildGraphFromExistingNodes(nodesToLoad, form.isStated(), graphBuilder, existingContentCriteria,
				queryConcept -> alternativeAncestors.addAll(Sets.difference(queryConcept.getAncestors(), nodesToLoad)));

		if (!alternativeAncestors.isEmpty()) {
			// Add alternative ancestors to graph. No need to collect any more this time.
			buildGraphFromExistingNodes(alternativeAncestors, form.isStated(), graphBuilder, existingContentCriteria,
					queryConcept -> {});
		}
		timer.checkpoint(format("Build existing graph from nodes. %s alternative ancestors found.", alternativeAncestors.size()));

		logger.info("{} existing nodes loaded.", graphBuilder.getNodeCount());
		return updateSource;
	}

	private void buildGraphFromExistingNodes(Set<Long> nodesToLoad, boolean stated, GraphBuilder graphBuilder, BranchCriteria branchCriteriaForAlreadyCommittedContent,
			Consumer<QueryConcept> alternativeAncestorCollector) {

		NativeSearchQueryBuilder queryConceptQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(QueryConcept.class))
						.must(termQuery(QueryConcept.Fields.STATED, stated))
						.filter(termsQuery(QueryConcept.Fields.CONCEPT_ID, nodesToLoad))
				)
				.withFields(QueryConcept.Fields.CONCEPT_ID, QueryConcept.Fields.PARENTS, QueryConcept.Fields.ANCESTORS)
				.withPageable(LARGE_PAGE);

		try (SearchHitsIterator<QueryConcept> queryConcepts = elasticsearchTemplate.searchForStream(queryConceptQuery.build(), QueryConcept.class)) {
			queryConcepts.forEachRemaining(hit -> {
				for (Long parent : hit.getContent().getParents()) {
					graphBuilder.addParent(hit.getContent().getConceptIdL(), parent);
				}

				// Collect ancestors of this concept which are not already marked for loading because of multiple parents.
				// These must also be loaded to prevent the alternative ancestors being lost.
				alternativeAncestorCollector.accept(hit.getContent());
			});
		}
	}

	private void axiomStreamToRelationshipStream(SearchHitsIterator<ReferenceSetMember> changedAxioms, Predicate<Relationship> relationshipPredicate,
			BiConsumer<SnomedComponent<?>, Relationship> relationshipConsumer) throws ConversionException {

		AtomicReference<ConversionException> exceptionHolder = new AtomicReference<>();// Used to hold exceptions thrown within the lambda function
		changedAxioms.forEachRemaining(hit -> {
			try {
				SAxiomRepresentation sAxiomRepresentation = axiomConversionService.convertAxiomMemberToAxiomRepresentation(hit.getContent());
				if (sAxiomRepresentation == null) {
					// Not a regular axiom so does not effect the semantic index
					return;
				}
				Long conceptId = sAxiomRepresentation.getLeftHandSideNamedConcept();
				Set<Relationship> relationships = sAxiomRepresentation.getRightHandSideRelationships();
				if (conceptId == null || relationships == null) {
					// Not a regular axiom so does not effect the semantic index
					return;
				}
				relationships.stream()
						.filter(relationshipPredicate)
						.forEach(relationship -> {
							relationship.setSourceId(conceptId.toString());
							relationshipConsumer.accept(hit.getContent(), relationship);
						});
			} catch (ConversionException e) {
				exceptionHolder.set(new ConversionException(format("Failed to convert axiom %s", hit.getContent().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION)), e));
			}
		});
		if (exceptionHolder.get() != null) {
			throw exceptionHolder.get();
		}
	}

	private void applyAttributeChanges(QueryConcept queryConcept, Long conceptId, Map<Long, AttributeChanges> conceptAttributeChanges) {
		queryConcept.clearAttributes();
		AttributeChanges attributeChanges = conceptAttributeChanges.get(conceptId);
		if (attributeChanges != null) {
			attributeChanges.getEffectiveSortedChanges().forEach(attributeChange ->
					queryConcept.addAttribute(attributeChange.getGroup(), attributeChange.getType(), attributeChange.getValue()));
		}
	}

	private void removeQConceptChangesOnBranch(Commit commit) {
		// End versions on branch
		versionControlHelper.endAllVersionsOnThisBranch(QueryConcept.class, null, commit, queryConceptRepository);

		// Restore versions from parent branches which were ended on this branch
		Branch branch = commit.getBranch();
		int parentVersionsRestored = branch.getVersionsReplaced(QueryConcept.class).size();
		Map<String, Set<String>> versionsReplaced = branch.getVersionsReplaced();
		versionsReplaced.put(QueryConcept.class.getSimpleName(), new HashSet<>());
		branch.setVersionsReplaced(versionsReplaced);
		logger.info("Restored visibility of {} query concepts from parents", parentVersionsRestored);
	}

	Collection<String> getParentPaths(String path) {
		List<String> parents = new ArrayList<>();
		while ((path = PathUtil.getParentPath(path)) != null) {
			parents.add(path);
		}
		return parents;
	}

	private void doSaveBatch(Collection<QueryConcept> queryConcepts, Commit commit) {
		doSaveBatchComponents(queryConcepts, commit, "conceptIdForm", queryConceptRepository);
	}

	private Set<Long> getInactiveOrMissingConceptIds(Set<Long> requiredActiveConcepts, BranchCriteria branchCriteria) {
		// We can't select the concepts which are not there!
		// For speed first we will count the concepts which are there and active
		// If the count doesn't match we load the ids of the concepts which are there so we can work out those which are not.
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
						.filter(termsQuery(Concept.Fields.CONCEPT_ID, requiredActiveConcepts))
				)
				.withFields(Concept.Fields.CONCEPT_ID)
				.withPageable(PageRequest.of(0, 1));

		Query activeQuery = queryBuilder.build();
		activeQuery.setTrackTotalHits(true);
		SearchHits<Concept> concepts = elasticsearchTemplate.search(activeQuery, Concept.class);
		if (concepts.getTotalHits() == requiredActiveConcepts.size()) {
			return Collections.emptySet();
		}

		// Some concepts are missing - let's collect them

		// Update query to collect concept ids efficiently
		queryBuilder.withPageable(LARGE_PAGE);
		Set<Long> missingConceptIds = new LongOpenHashSet(requiredActiveConcepts);
		try (SearchHitsIterator<Concept> stream = elasticsearchTemplate.searchForStream(queryBuilder.build(), Concept.class)) {
			stream.forEachRemaining(hit -> missingConceptIds.remove(hit.getContent().getConceptIdAsLong()));
		}

		return missingConceptIds;
	}

	private static final class AttributeChanges {

		private static final Comparator<AttributeChange> comparator = Comparator
				.comparing(AttributeChange::getEffectiveTime);

		private final List<AttributeChange> changes;

		private AttributeChanges() {
			changes = new ArrayList<>();
		}

		private void addAttribute(Integer effectiveTime, int groupId, Long type, Object value) {
			changes.add(new AttributeChange(effectiveTime, groupId, type, value));
		}

		private List<AttributeChange> getEffectiveSortedChanges() {
			changes.sort(comparator);
			return changes;
		}

		@Override
		public String toString() {
			return "AttributeChanges{" +
					"changes=" + changes +
					'}';
		}
	}

	private static final class AttributeChange {

		private final int effectiveTime;
		private final int group;
		private final long type;
		private final Object value;

		private AttributeChange(Integer effectiveTime, int group, long type, Object value) {
			if (effectiveTime == null) {
				effectiveTime = 90000000;
			}
			this.effectiveTime = effectiveTime;
			this.group = group;
			this.type = type;
			this.value = value;
		}

		private int getEffectiveTime() {
			return effectiveTime;
		}

		private int getGroup() {
			return group;
		}

		private long getType() {
			return type;
		}

		private Object getValue() {
			return value;
		}

		@Override
		public String toString() {
			return "AttributeChange{" +
					"effectiveTime=" + effectiveTime +
					", group=" + group +
					", type=" + type +
					", value=" + value +
					'}';
		}
	}
}
