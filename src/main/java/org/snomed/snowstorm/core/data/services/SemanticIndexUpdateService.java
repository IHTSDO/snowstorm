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

@Service
public class SemanticIndexUpdateService extends ComponentService implements CommitListener {

	private static final long CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG = parseLong(Concepts.CONCEPT_MODEL_OBJECT_ATTRIBUTE);
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

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (semanticIndexingEnabled) {
			try {
				updateStatedAndInferredSemanticIndex(commit);
			} catch (ConversionException | GraphBuilderException e) {
				throw new IllegalStateException("Failed to update semantic index. " + e.getMessage(), e);
			}
		} else {
			logger.info("Semantic indexing is disabled.");
		}
	}

	public void rebuildStatedAndInferredSemanticIndex(String branchPath) throws ServiceException {
		try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata("Rebuilding semantic index."))) {
			rebuildSemanticIndex(commit);
			commit.markSuccessful();
		} catch (ConversionException | GraphBuilderException e) {
			throw new ServiceException("Failed to update semantic index. " + e.getMessage(), e);
		}
	}

	private void updateStatedAndInferredSemanticIndex(Commit commit) throws IllegalStateException, ConversionException, GraphBuilderException {
		if (commit.isRebase()) {
			rebuildSemanticIndex(commit);
		} else if (commit.getCommitType() != Commit.CommitType.PROMOTION) {
			// Update query index using changes in the current commit
			BranchCriteria changesBranchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
			List<Branch> timeSlice = versionControlHelper.getTimeSlice(commit.getBranch().getPath(), commit.getTimepoint());
			Set<String> relationshipAndAxiomDeletionsToProcess = Sets.union(commit.getEntityVersionsReplaced().getOrDefault(ReferenceSetMember.class.getSimpleName(), Collections.emptySet()),
					commit.getEntityVersionsReplaced().getOrDefault(Relationship.class.getSimpleName(), Collections.emptySet()));
			updateSemanticIndex(Form.STATED, changesBranchCriteria, relationshipAndAxiomDeletionsToProcess, commit, timeSlice, false);
			updateSemanticIndex(Form.INFERRED, changesBranchCriteria, relationshipAndAxiomDeletionsToProcess, commit, timeSlice, false);
		}
		// If promotion the semantic changes will be promoted with the rest of the content.
	}

	private void rebuildSemanticIndex(Commit commit) throws ConversionException, GraphBuilderException {
		// Recreate query index using new parent base point + content on this branch
		Branch branch = commit.getBranch();
		removeQConceptChangesOnBranch(commit);

		BranchCriteria changesBranchCriteria = versionControlHelper.getChangesOnBranchCriteria(branch);
		List<Branch> timeSlice = versionControlHelper.getTimeSlice(branch.getPath(), commit.getTimepoint());
		Set<String> relationshipAndAxiomDeletionsToProcess = Sets.union(branch.getVersionsReplaced(ReferenceSetMember.class), branch.getVersionsReplaced(Relationship.class));
		boolean completeRebuild = branch.getPath().equals("MAIN");
		updateSemanticIndex(Form.STATED, changesBranchCriteria, relationshipAndAxiomDeletionsToProcess, commit, timeSlice, completeRebuild);
		updateSemanticIndex(Form.INFERRED, changesBranchCriteria, relationshipAndAxiomDeletionsToProcess, commit, timeSlice, completeRebuild);
	}

	private void updateSemanticIndex(Form form, BranchCriteria changesBranchCriteria, Set<String> internalIdsOfDeletedComponents, Commit commit,
			List<Branch> timeSlice, boolean completeRebuild) throws IllegalStateException, ConversionException, GraphBuilderException {

		// Note: Searches within this method use a filter clause for collections of identifiers because these
		//       can become larger than the maximum permitted query criteria.

		TimerUtil timer = new TimerUtil("TC index " + form.getName(), Level.INFO, 1);
		String branchPath = commit.getBranch().getPath();
		BranchCriteria branchCriteriaForAlreadyCommittedContent = versionControlHelper.getBranchCriteriaBeforeOpenCommit(commit);
		BranchCriteria branchCriteriaIncludingOpenCommit = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		timer.checkpoint("get branch criteria");

		// Identify concepts with modeling changes  and load relevant parts of the existing node graph
		final GraphBuilder graphBuilder = new GraphBuilder();
		Set<Long> updatedConceptIds = buildRelevantPartsOfExistingGraph(graphBuilder, completeRebuild, form,
				changesBranchCriteria, branchCriteriaForAlreadyCommittedContent, internalIdsOfDeletedComponents, timer);
		if (updatedConceptIds.isEmpty()) {
			// Nothing to do
			return;
		}

		// Step - Update graph
		// Strategy: Clear the modelling of updated concepts then add/remove edges and attributes based on the new commit
		boolean newGraph = graphBuilder.getNodeCount() == 0;
		Set<Long> requiredActiveConcepts = new LongOpenHashSet();
		Map<Long, AttributeChanges> conceptAttributeChanges = new Long2ObjectOpenHashMap<>();

		// Clear parents of updated concepts
		for (Long updatedConceptId : updatedConceptIds) {
			graphBuilder.clearParentsAndMarkUpdated(updatedConceptId);
		}

		BiConsumer<SnomedComponent, Relationship> relationshipConsumer = (component, relationship) -> {
			if (!relationship.isConcrete() && activeNow(component, timeSlice)) {
				long conceptId = parseLong(relationship.getSourceId());
				int groupId = relationship.getGroupId();
				long type = parseLong(relationship.getTypeId());
				long value = parseLong(relationship.getDestinationId());
				Integer effectiveTime = component.getEffectiveTimeI();
				if (type == IS_A_TYPE) {
					graphBuilder.addParent(conceptId, value);

					// Concept model object attribute is not linked to the concept hierarchy by any axiom
					// however we want the link in the semantic index so let's add it here.
					// TODO: Remove this - there is an axiom for this link now.
					if (value == CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG) {
						graphBuilder.addParent(CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG, CONCEPT_MODEL_ATTRIBUTE_LONG);
					}
				} else {
					conceptAttributeChanges.computeIfAbsent(conceptId, (c) -> new AttributeChanges()).addAttribute(effectiveTime, groupId, type, value);
				}
				requiredActiveConcepts.add(conceptId);
				requiredActiveConcepts.add(type);
				requiredActiveConcepts.add(value);
			}
		};

		try (final SearchHitsIterator<Relationship> activeRelationships = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteriaIncludingOpenCommit.getEntityBranchCriteria(Relationship.class))
						.must(termQuery(Relationship.Fields.ACTIVE, true))
						.must(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, form.getCharacteristicTypeIds()))
						.must(existsQuery(Relationship.Fields.DESTINATION_ID)) //todo Fix for Concrete ECL
						.filter(termsQuery(Relationship.Fields.SOURCE_ID, updatedConceptIds))
				)
				.withSort(SortBuilders.fieldSort(Relationship.Fields.EFFECTIVE_TIME))
				.withSort(SortBuilders.fieldSort(Relationship.Fields.ACTIVE))
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
			activeRelationships.forEachRemaining(hit -> relationshipConsumer.accept(hit.getContent(), hit.getContent()));
		}
		timer.checkpoint("Update graph using relationships of concepts with changed modelling.");

		if (form.isStated()) {
			for (List<Long> batch : Iterables.partition(updatedConceptIds, CLAUSE_LIMIT)) {
				try (final SearchHitsIterator<ReferenceSetMember> activeAxioms = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(branchCriteriaIncludingOpenCommit.getEntityBranchCriteria(ReferenceSetMember.class))
								.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
								.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
								.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, batch))
						)
						.withSort(SortBuilders.fieldSort(Relationship.Fields.EFFECTIVE_TIME))
						.withSort(SortBuilders.fieldSort(Relationship.Fields.ACTIVE))
						.withSort(SortBuilders.fieldSort("start"))
						.withPageable(ConceptService.LARGE_PAGE).build(), ReferenceSetMember.class)) {
					axiomStreamToRelationshipStream(activeAxioms, relationship -> true, relationshipConsumer);
				}
			}
			timer.checkpoint("Update graph using axioms of concepts with changed modelling.");
		}

		Set<Long> inactiveOrMissingConceptIds = new LongOpenHashSet();
		for (List<Long> batch : Iterables.partition(requiredActiveConcepts, CLAUSE_LIMIT)) {
			inactiveOrMissingConceptIds.addAll(getInactiveOrMissingConceptIds(Sets.newHashSet(batch), branchCriteriaIncludingOpenCommit));
		}
		if (!inactiveOrMissingConceptIds.isEmpty()) {
			logger.warn("The following concepts have been referred to in relationships but are missing or inactive: " + inactiveOrMissingConceptIds);
		}

		// Step: Save changes
		Map<Long, Node> nodesToSave = new Long2ObjectOpenHashMap<>();
		graphBuilder.getNodes().stream()
				.filter(node -> newGraph || completeRebuild || node.isAncestorOrSelfUpdated() || conceptAttributeChanges.containsKey(node.getId()))
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

		try (final SearchHitsIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.STATED, form.isStated()))
						.filter(boolQuery()
								// Exclude those QueryConcepts which were removed in this commit
								.mustNot(boolQuery()
										.must(termQuery("path", branchPath))
										.must(termQuery("end", commit.getTimepoint().getTime()))
								)
								.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIdsToUpdate)))
				)
				.withPageable(ConceptService.LARGE_PAGE).build(), QueryConcept.class)) {
			while (existingQueryConcepts.hasNext()) {
				QueryConcept queryConcept = existingQueryConcepts.next().getContent();
				Long conceptId = queryConcept.getConceptIdL();
				Node node = nodesToSave.get(conceptId);
				boolean save = false;
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
			final Set<Long> transitiveClosure = new HashSet<>(node.getTransitiveClosure(branchPath, throwExceptionIfTransitiveClosureLoopFound));
			final Set<Long> parentIds = node.getParents().stream().map(Node::getId).collect(Collectors.toSet());
			QueryConcept queryConcept = new QueryConcept(nodeId, parentIds, transitiveClosure, form.isStated());
			applyAttributeChanges(queryConcept, nodeId, conceptAttributeChanges);
			queryConceptsToSave.add(queryConcept);
		}
		if (!queryConceptsToSave.isEmpty()) {

			// Delete query concepts which have no parents
			queryConceptsToSave.stream().filter(c -> c.getParents().isEmpty() && !c.getConceptIdL().toString().equals(Concepts.SNOMEDCT_ROOT)).forEach(Entity::markDeleted);
			queryConceptsToSave.forEach(QueryConcept::serializeGroupedAttributesMap);

			// Save in batches
			for (List<QueryConcept> queryConcepts : Iterables.partition(queryConceptsToSave, Config.BATCH_SAVE_SIZE)) {
				doSaveBatch(queryConcepts, commit);
			}
		}
		timer.checkpoint("Save updated QueryConcepts");
		logger.debug("{} concepts updated within the {} semantic index.", queryConceptsToSave.size(), form.getName());

		timer.finish();
	}

	private boolean activeNow(SnomedComponent component, List<Branch> timeSlice) {
		if (!component.isActive()) {
			return false;
		}
		Date end = component.getEnd();
		if (end == null) {
			return true;
		}
		String path = component.getPath();
		for (Branch branchOnStack : timeSlice) {
			if (path.equals(branchOnStack.getPath())) {
				return branchOnStack.getHead().before(end);
			}
		}
		logger.error("Component {} processed with a path {} which is not in the branch stack for {}", component.getId(), component.getPath(), timeSlice.get(0).getPath());
		return false;
	}

	private Set<Long> buildRelevantPartsOfExistingGraph(GraphBuilder graphBuilder, boolean completeRebuild, Form form,
			BranchCriteria changesBranchCriteria, BranchCriteria branchCriteriaForAlreadyCommittedContent,
			Set<String> internalIdsOfDeletedComponents, TimerUtil timer) throws ConversionException {

		Set<Long> updateSource = new LongOpenHashSet();
		Set<Long> updateDestination = new LongOpenHashSet();
		Set<Long> existingAncestors = new LongOpenHashSet();
		Set<Long> existingDescendants = new LongOpenHashSet();

		if (completeRebuild) {
			logger.info("Performing rebuild of {} semantic index", form.getName());
		}
		else {
			// Step: Collect source and destinations of changed is-a relationships
			try (final SearchHitsIterator<Relationship> changedIsARelationships = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery().filter(
							boolQuery()
									.must(termQuery("typeId", Concepts.ISA))
									.must(termsQuery("characteristicTypeId", form.getCharacteristicTypeIds()))
									.must(boolQuery()
											// Either on this branch
											.should(changesBranchCriteria.getEntityBranchCriteria(Relationship.class))
											// Or on parent branch and deleted/replaced on this branch
											.should(idsQuery().addIds(internalIdsOfDeletedComponents.toArray(new String[]{})))
									)
							)
					)
					.withFields(Relationship.Fields.SOURCE_ID, Relationship.Fields.DESTINATION_ID)
					.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
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
												.should(changesBranchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
												// Or on parent branch and deleted/replaced on this branch
												.should(termsQuery("internalId", internalIdsOfDeletedComponents))
										)
								)
						)
						.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_FIELD_PATH)
						.withPageable(ConceptService.LARGE_PAGE).build(), ReferenceSetMember.class)) {
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
											.should(changesBranchCriteria.getEntityBranchCriteria(Relationship.class))
											// Or on parent branch and deleted/replaced on this branch
											.should(termsQuery("internalId", internalIdsOfDeletedComponents))
									)
									// Skip concepts already in the list
									.mustNot(termsQuery(Relationship.Fields.SOURCE_ID, updateSource))
							)
					)
					.withFields(Relationship.Fields.SOURCE_ID)
					.withPageable(ConceptService.LARGE_PAGE)
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
							.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(QueryConcept.class))
							.must(termsQuery("stated", form.isStated()))
							.filter(termsQuery(QueryConcept.Fields.CONCEPT_ID, Sets.union(updateSource, updateDestination)))
					)
					.withFields(QueryConcept.Fields.ANCESTORS)
					.withPageable(ConceptService.LARGE_PAGE).build();
			try (final SearchHitsIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.searchForStream(query, QueryConcept.class)) {
				existingQueryConcepts.forEachRemaining(hit -> existingAncestors.addAll(hit.getContent().getAncestors()));
			}
			timer.checkpoint("Collect existingAncestors from QueryConcept.");

			// Step: Identify existing descendants
			// Strategy: Find existing nodes where TC matches updated relationship source ids
			try (final SearchHitsIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(QueryConcept.class))
							.must(termsQuery("stated", form.isStated()))
							.filter(termsQuery("ancestors", updateSource))
					)
					.withFields(QueryConcept.Fields.CONCEPT_ID)
					.withPageable(ConceptService.LARGE_PAGE).build(), QueryConcept.class)) {
				existingQueryConcepts.forEachRemaining(hit -> existingDescendants.add(hit.getContent().getConceptIdL()));
			}
			timer.checkpoint("Collect existingDescendants from QueryConcept.");

			logger.debug("{} existing ancestors and {} existing descendants of updated relationships identified.", existingAncestors.size(), existingDescendants.size());
		}

		// Step: Build existing graph
		if (!completeRebuild) {
			// Iterative update.
			// Strategy: Load selection of existing nodes and use parents to build graph.
			Set<Long> nodesToLoad = new LongOpenHashSet();
			nodesToLoad.addAll(existingAncestors);
			nodesToLoad.addAll(existingDescendants);
			nodesToLoad.addAll(updateSource);
			nodesToLoad.addAll(updateDestination);

			// Build graph, collecting any alternative ancestors which have been missed.
			Set<Long> alternativeAncestors = new LongOpenHashSet();
			buildGraphFromExistingNodes(nodesToLoad, form.isStated(), graphBuilder, branchCriteriaForAlreadyCommittedContent,
					queryConcept -> alternativeAncestors.addAll(Sets.difference(queryConcept.getAncestors(), nodesToLoad)));

			if (!alternativeAncestors.isEmpty()) {
				// Add alternative ancestors to graph. No need to collect any more this time.
				buildGraphFromExistingNodes(alternativeAncestors, form.isStated(), graphBuilder, branchCriteriaForAlreadyCommittedContent,
						queryConcept -> {});
			}
			timer.checkpoint(format("Build existing graph from nodes. %s alternative ancestors found.", alternativeAncestors.size()));
		} else {
			// Rebuild from scratch.
			// Strategy: Find relationships of existing TC and descendant nodes and build existing graph(s)

			NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(Relationship.class))
							.must(termQuery("active", true))
							.must(termQuery("typeId", Concepts.ISA))
							.must(termsQuery("characteristicTypeId", form.getCharacteristicTypeIds()))
					)
					.withFields(Relationship.Fields.SOURCE_ID, Relationship.Fields.DESTINATION_ID)
					.withPageable(ConceptService.LARGE_PAGE);

			try (final SearchHitsIterator<Relationship> existingIsARelationships = elasticsearchTemplate.searchForStream(queryBuilder.build(), Relationship.class)) {
				existingIsARelationships.forEachRemaining(hit -> {
					long sourceId = parseLong(hit.getContent().getSourceId());
					graphBuilder.addParent(sourceId, parseLong(hit.getContent().getDestinationId()));
					updateSource.add(sourceId);
				});
			}
			timer.checkpoint("Build existing graph from Relationships.");
			if (form.isStated()) {
				NativeSearchQueryBuilder axiomQueryBuilder = new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(ReferenceSetMember.class))
								.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
								.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
						)
						.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_FIELD_PATH)
						.withPageable(ConceptService.LARGE_PAGE);
				try (final SearchHitsIterator<ReferenceSetMember> axiomStream = elasticsearchTemplate.searchForStream(axiomQueryBuilder.build(), ReferenceSetMember.class)) {
					axiomStreamToRelationshipStream(
							axiomStream,
							relationship -> relationship.getTypeId().equals(Concepts.ISA),
							(component, relationship) -> {
								long sourceId = parseLong(relationship.getSourceId());
								graphBuilder.addParent(sourceId, parseLong(relationship.getDestinationId()));
								updateSource.add(sourceId);
							}
					);
				}
				timer.checkpoint("Build existing graph from Axioms.");
			}
		}

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
			BiConsumer<SnomedComponent, Relationship> relationshipConsumer) throws ConversionException {

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

		private List<AttributeChange> changes;

		private AttributeChanges() {
			changes = new ArrayList<>();
		}

		private void addAttribute(Integer effectiveTime, int groupId, Long type, Long value) {
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
		private final long value;

		private AttributeChange(Integer effectiveTime, int group, long type, long value) {
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

		private long getValue() {
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
