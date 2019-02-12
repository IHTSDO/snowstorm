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
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.QueryConceptRepository;
import org.snomed.snowstorm.core.data.services.pojo.SAxiomRepresentation;
import org.snomed.snowstorm.core.data.services.transitiveclosure.GraphBuilder;
import org.snomed.snowstorm.core.data.services.transitiveclosure.Node;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class SemanticIndexUpdateService extends ComponentService implements CommitListener {

	private static final long CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG = parseLong(Concepts.CONCEPT_MODEL_OBJECT_ATTRIBUTE);
	private static final long CONCEPT_MODEL_ATTRIBUTE_LONG = parseLong(Concepts.CONCEPT_MODEL_ATTRIBUTE);

	@Value("${commit-hook.semantic-indexing.enabled:true}")
	private boolean semanticIndexingEnabled;

	static final int BATCH_SAVE_SIZE = 10000;
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
	private AxiomConversionService axiomConversionService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		branchService.addCommitListener(this);
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (semanticIndexingEnabled) {
			try {
				updateStatedAndInferredSemanticIndex(commit);
			} catch (ConversionException e) {
				throw new IllegalStateException("Failed to convert OWL Axioms.", e);
			}
		} else {
			logger.info("Semantic indexing is disabled.");
		}
	}

	public void rebuildStatedAndInferredSemanticIndex(String branch) throws ConversionException {
		// TODO: Only use on MAIN
		try (Commit commit = branchService.openCommit(branch)) {
			BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(commit.getBranch());
			updateSemanticIndex(true, branchCriteria, Collections.emptySet(), commit, true);
			updateSemanticIndex(false, branchCriteria, Collections.emptySet(), commit, true);
			commit.markSuccessful();
		}
	}

	private void updateStatedAndInferredSemanticIndex(Commit commit) throws IllegalStateException, ConversionException {
		if (commit.isRebase()) {
			// Recreate query index using new parent base point + content on this branch
			Branch branch = commit.getBranch();
			removeQConceptChangesOnBranch(commit);

			BranchCriteria changesBranchCriteria = versionControlHelper.getChangesOnBranchCriteria(branch);
			Set<String> relationshipAndAxiomDeletionsToProcess = new HashSet<>(branch.getVersionsReplaced(ReferenceSetMember.class));
			relationshipAndAxiomDeletionsToProcess.addAll(branch.getVersionsReplaced(Relationship.class));
			updateSemanticIndex(true, changesBranchCriteria, relationshipAndAxiomDeletionsToProcess, commit, false);
			updateSemanticIndex(false, changesBranchCriteria, relationshipAndAxiomDeletionsToProcess, commit, false);
		} else {
			// Update query index using changes in the current commit
			BranchCriteria changesBranchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
			Set<String> deletedComponents = commit.getEntityVersionsDeleted();
			updateSemanticIndex(true, changesBranchCriteria, deletedComponents, commit, false);
			updateSemanticIndex(false, changesBranchCriteria, deletedComponents, commit, false);
		}
	}

	private void updateSemanticIndex(boolean stated, BranchCriteria changesBranchCriteria, Set<String> relationshipAndAxiomDeletionsToProcess, Commit commit, boolean rebuild) throws IllegalStateException, ConversionException {
		// Note: Searches within this method use a filter clause for collections of identifiers because these
		//       can become larger than the maximum permitted query criteria.

		String formName;
		Set<String> characteristicTypeIds = new HashSet<>();
		if (stated) {
			formName = "stated";
			characteristicTypeIds.add(Concepts.STATED_RELATIONSHIP);
			characteristicTypeIds.add(Concepts.ADDITIONAL_RELATIONSHIP);
		} else {
			formName = "inferred";
			characteristicTypeIds.add(Concepts.INFERRED_RELATIONSHIP);
			characteristicTypeIds.add(Concepts.ADDITIONAL_RELATIONSHIP);// Additional relationships should be in both forms.
		}

		TimerUtil timer = new TimerUtil("TC index " + formName, Level.INFO);
		Set<Long> updateSource = new HashSet<>();
		Set<Long> updateDestination = new HashSet<>();
		Set<Long> existingAncestors = new HashSet<>();
		Set<Long> existingDescendants = new HashSet<>();

		String branchPath = commit.getBranch().getPath();
		BranchCriteria branchCriteriaForAlreadyCommittedContent = versionControlHelper.getBranchCriteriaBeforeOpenCommit(commit);
		timer.checkpoint("get branch criteria");
		if (rebuild) {
			logger.info("Performing {} of {} semantic index", "rebuild", formName);
		} else {
			// Step: Collect source and destinations of changed is-a relationships
			try (final CloseableIterator<Relationship> changedIsARelationships = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(changesBranchCriteria.getEntityBranchCriteria(Relationship.class))
							.must(termQuery("typeId", Concepts.ISA))
							.must(termsQuery("characteristicTypeId", characteristicTypeIds))
					)
					.withFields(Relationship.Fields.SOURCE_ID, Relationship.Fields.DESTINATION_ID)
					.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
				changedIsARelationships.forEachRemaining(relationship -> {
					updateSource.add(parseLong(relationship.getSourceId()));
					updateDestination.add(parseLong(relationship.getDestinationId()));
				});
			}
			timer.checkpoint("Collect changed is-a relationships.");

			boolean someChangedAxioms = false;
			if (stated) {
				// Step: Collect source and destinations of is-a fragments within changed axioms
				try (final CloseableIterator<ReferenceSetMember> changedAxioms = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(changesBranchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
								.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
						)
						.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_FIELD_PATH)
						.withPageable(ConceptService.LARGE_PAGE).build(), ReferenceSetMember.class)) {
					someChangedAxioms = changedAxioms.hasNext();
					axiomStreamToRelationshipStream(
							changedAxioms,
							// filter
							relationship -> relationship.getTypeId().equals(Concepts.ISA),
							// for each
							(component, relationship) -> {
								updateSource.add(parseLong(relationship.getSourceId()));
								updateDestination.add(parseLong(relationship.getDestinationId()));
							});
				}
				if (updateDestination.contains(CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG)) {
					updateDestination.add(CONCEPT_MODEL_ATTRIBUTE_LONG);
				}
				timer.checkpoint("Collect changed axiom is-a fragments.");
			}

			if (updateSource.isEmpty() && !someChangedAxioms) {
				// There are no is-a relationships changes
				// Are there any other relationship changes?
				long notIsAUpdateCount = elasticsearchTemplate.count(new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(changesBranchCriteria.getEntityBranchCriteria(Relationship.class))
								.mustNot(termQuery("typeId", Concepts.ISA))
								.must(termsQuery("characteristicTypeId", characteristicTypeIds))
						).build(), Relationship.class);

				if (notIsAUpdateCount == 0) {
					// Stop here - nothing to update
					return;
				}
			}

			logger.info("Performing {} of {} semantic index", "incremental update", formName);

			// Identify parts of the graph that nodes are moving from or to

			// Step: Identify existing TC of updated nodes
			// Strategy: Find existing nodes where ID matches updated relationship source or destination ids, record TC
			NativeSearchQuery query = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(QueryConcept.class))
							.must(termsQuery("stated", stated))
					)
					.withFields(QueryConcept.Fields.ANCESTORS)
					.withFilter(boolQuery()
							.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, Sets.union(updateSource, updateDestination))))
					.withPageable(ConceptService.LARGE_PAGE).build();
			try (final CloseableIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.stream(query, QueryConcept.class)) {
				existingQueryConcepts.forEachRemaining(queryConcept -> existingAncestors.addAll(queryConcept.getAncestors()));
			}
			timer.checkpoint("Collect existingAncestors from QueryConcept.");

			// Step: Identify existing descendants
			// Strategy: Find existing nodes where TC matches updated relationship source ids
			try (final CloseableIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(QueryConcept.class))
							.must(termsQuery("stated", stated))
					)
					.withFields(QueryConcept.Fields.CONCEPT_ID)
					.withFilter(boolQuery()
							.must(termsQuery("ancestors", updateSource)))
					.withPageable(ConceptService.LARGE_PAGE).build(), QueryConcept.class)) {
				existingQueryConcepts.forEachRemaining(queryConcept -> existingDescendants.add(queryConcept.getConceptIdL()));
			}
			timer.checkpoint("Collect existingDescendants from QueryConcept.");

			logger.debug("{} existing ancestors and {} existing descendants of updated relationships identified.", existingAncestors.size(), existingDescendants.size());
		}

		// Step: Build existing graph
		// Strategy: Find relationships of existing TC and descendant nodes and build existing graph(s)
		final GraphBuilder graphBuilder = new GraphBuilder();
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(Relationship.class))
						.must(termQuery("active", true))
						.must(termQuery("typeId", Concepts.ISA))
						.must(termsQuery("characteristicTypeId", characteristicTypeIds))
				)
				.withFields(Relationship.Fields.SOURCE_ID, Relationship.Fields.DESTINATION_ID)
				.withPageable(ConceptService.LARGE_PAGE);
		Set<Long> nodesToLoad = new HashSet<>();
		if (!rebuild) {
			nodesToLoad.addAll(existingAncestors);
			nodesToLoad.addAll(existingDescendants);
			nodesToLoad.addAll(updateSource);
			nodesToLoad.addAll(updateDestination);
			queryBuilder.withFilter(boolQuery().must(termsQuery("sourceId", nodesToLoad)));
		}
		try (final CloseableIterator<Relationship> existingIsARelationships = elasticsearchTemplate.stream(queryBuilder.build(), Relationship.class)) {
			existingIsARelationships.forEachRemaining(relationship ->
					graphBuilder.addParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId())));
		}
		timer.checkpoint("Build existing nodes from Relationships.");
		if (stated) {
			NativeSearchQueryBuilder axiomQueryBuilder = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
							.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
					)
					.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_FIELD_PATH)
					.withPageable(ConceptService.LARGE_PAGE);
			if (!rebuild) {
				axiomQueryBuilder.withFilter(boolQuery().must(termsQuery(ReferenceSetMember.Fields.CONCEPT_ID, nodesToLoad)));
			}
			try (final CloseableIterator<ReferenceSetMember> axiomStream = elasticsearchTemplate.stream(axiomQueryBuilder.build(), ReferenceSetMember.class)) {
				axiomStreamToRelationshipStream(
						axiomStream,
						relationship -> relationship.getTypeId().equals(Concepts.ISA),
						(component, relationship) -> graphBuilder.addParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()))
				);
			}
			timer.checkpoint("Build existing nodes from Axioms.");
		}
		logger.info("{} existing nodes loaded.", graphBuilder.getNodeCount());


		// Step - Update graph
		// Strategy: Add/remove edges from new commit
		// Also collect other attribute changes
		AtomicLong relationshipsAdded = new AtomicLong();
		AtomicLong relationshipsRemoved = new AtomicLong();
		boolean newGraph = graphBuilder.getNodeCount() == 0;
		Set<Long> requiredActiveConcepts = new LongOpenHashSet();
		Map<Long, AttributeChanges> conceptAttributeChanges = new Long2ObjectOpenHashMap<>();

		BiConsumer<SnomedComponent, Relationship> relationshipConsumer = (component, relationship) -> {
			boolean ignore = false;
			boolean justDeleted = false;
			if (component.getEnd() != null) {
				if (component instanceof ReferenceSetMember
						// Assume Axiom fragments are removed as we don't have better information here. The fragments will be added again if still present in a new version.

						|| relationshipAndAxiomDeletionsToProcess.contains(component.getId())) {
					justDeleted = true;
				} else {
					// Replaced not deleted. A new version will be in the selection.
					ignore = true;
				}
			}
			if (!ignore) {
				long conceptId = parseLong(relationship.getSourceId());
				int groupId = relationship.getGroupId();
				long type = parseLong(relationship.getTypeId());
				long value = parseLong(relationship.getDestinationId());
				Integer effectiveTime = component.getEffectiveTimeI();
				if (!justDeleted && component.isActive()) {
					if (type == IS_A_TYPE) {
						graphBuilder.addParent(conceptId, value)
								.markUpdated();
						relationshipsAdded.incrementAndGet();

						// Concept model object attribute is not linked to the concept hierarchy by any axiom
						// however we want the link in the semantic index so let's add it here.
						if (value == CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG) {
							graphBuilder.addParent(CONCEPT_MODEL_OBJECT_ATTRIBUTE_LONG, CONCEPT_MODEL_ATTRIBUTE_LONG)
									.markUpdated();
						}
					} else {
						conceptAttributeChanges.computeIfAbsent(conceptId, (c) -> new AttributeChanges()).addAttribute(effectiveTime, groupId, type, value);
					}
					requiredActiveConcepts.add(conceptId);
					requiredActiveConcepts.add(type);
					requiredActiveConcepts.add(value);
				} else {
					if (type == IS_A_TYPE) {
						Node node = graphBuilder.removeParent(conceptId, value);
						if (node != null) {
							node.markUpdated();
						}
						relationshipsRemoved.incrementAndGet();
					} else {
						conceptAttributeChanges.computeIfAbsent(conceptId, (c) -> new AttributeChanges()).removeAttribute(effectiveTime, groupId, type, value);
					}
				}
			}
		};

		try (final CloseableIterator<Relationship> relationshipChanges = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(changesBranchCriteria.getEntityBranchCriteria(Relationship.class))
						.must(termsQuery("characteristicTypeId", characteristicTypeIds))
				)
				.withSort(SortBuilders.fieldSort(Relationship.Fields.EFFECTIVE_TIME))
				.withSort(SortBuilders.fieldSort(Relationship.Fields.ACTIVE))
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
			relationshipChanges.forEachRemaining(relationship -> relationshipConsumer.accept(relationship, relationship));
		}
		timer.checkpoint("Update graph using changed relationships.");

		if (stated) {
			try (final CloseableIterator<ReferenceSetMember> axiomChanges = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(changesBranchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
					)
					.withSort(SortBuilders.fieldSort(Relationship.Fields.EFFECTIVE_TIME))
					.withSort(SortBuilders.fieldSort(Relationship.Fields.ACTIVE))
					.withSort(SortBuilders.fieldSort("start"))
					.withPageable(ConceptService.LARGE_PAGE).build(), ReferenceSetMember.class)) {
				axiomStreamToRelationshipStream(axiomChanges, relationship -> true, relationshipConsumer);
			}
			timer.checkpoint("Update graph using changed axioms.");
		}

		logger.debug("{} {} relationships added, {} inactive/removed.", relationshipsAdded.get(), formName, relationshipsRemoved.get());

		Set<Long> inactiveOrMissingConceptIds = getInactiveOrMissingConceptIds(requiredActiveConcepts, versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit));
		if (!inactiveOrMissingConceptIds.isEmpty()) {
			logger.warn("The following concepts have been referred to in relationships but are missing or inactive: " + inactiveOrMissingConceptIds);
		}

		// Step: Save changes
		Map<Long, Node> nodesToSave = new Long2ObjectOpenHashMap<>();
		graphBuilder.getNodes().stream()
				.filter(node -> newGraph || rebuild || node.isAncestorOrSelfUpdated(branchPath) || conceptAttributeChanges.containsKey(node.getId()))
				.forEach(node -> nodesToSave.put(node.getId(), node));
		Set<Long> nodesNotFound = new LongOpenHashSet(nodesToSave.keySet());
		Set<QueryConcept> queryConceptsToSave = new HashSet<>();

		// Collect ids of nodes and attribute updates and convert to conceptIdForm
		Set<Long> conceptIdsToUpdate = new LongOpenHashSet(nodesToSave.keySet());
		conceptIdsToUpdate.addAll(conceptAttributeChanges.keySet());
		List<String> conceptIdFormsToMatch = conceptIdsToUpdate.stream().map(id -> QueryConcept.toConceptIdForm(id, stated)).collect(Collectors.toList());

		try (final CloseableIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteriaForAlreadyCommittedContent.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.STATED, stated))
				)
				.withFilter(boolQuery()
						.must(termsQuery(QueryConcept.Fields.CONCEPT_ID_FORM, conceptIdFormsToMatch)))
				.withPageable(ConceptService.LARGE_PAGE).build(), QueryConcept.class)) {
			existingQueryConcepts.forEachRemaining(queryConcept -> {
				Long conceptId = queryConcept.getConceptIdL();
				Node node = nodesToSave.get(conceptId);
				if (node != null) {
					// TC changes
					queryConcept.setParents(node.getParents().stream().map(Node::getId).collect(Collectors.toSet()));
					queryConcept.setAncestors(node.getTransitiveClosure(branchPath));
				}
				applyAttributeChanges(queryConcept, conceptId, conceptAttributeChanges);
				queryConceptsToSave.add(queryConcept);
				nodesNotFound.remove(conceptId);
			});
		}

		timer.checkpoint("Collect existingDescendants from QueryConcept.");

		nodesNotFound.forEach(nodeId -> {
			Node node = nodesToSave.get(nodeId);
			final Set<Long> transitiveClosure = node.getTransitiveClosure(branchPath);
			final Set<Long> parentIds = node.getParents().stream().map(Node::getId).collect(Collectors.toSet());
			QueryConcept queryConcept = new QueryConcept(nodeId, parentIds, transitiveClosure, stated);
			applyAttributeChanges(queryConcept, nodeId, conceptAttributeChanges);
			queryConceptsToSave.add(queryConcept);
		});
		if (!queryConceptsToSave.isEmpty()) {

			// Delete query concepts which have no parents
			queryConceptsToSave.stream().filter(c -> c.getParents().isEmpty() && !c.getConceptIdL().toString().equals(Concepts.SNOMEDCT_ROOT)).forEach(Entity::markDeleted);

			// Save in batches
			for (List<QueryConcept> queryConcepts : Iterables.partition(queryConceptsToSave, BATCH_SAVE_SIZE)) {
				doSaveBatch(queryConcepts, commit);
			}
		}
		timer.checkpoint("Save updated QueryConcepts");
		logger.debug("{} concepts updated within the {} semantic index.", queryConceptsToSave.size(), formName);

		timer.finish();
	}

	private void axiomStreamToRelationshipStream(CloseableIterator<ReferenceSetMember> changedAxioms, Predicate<Relationship> relationshipPredicate,
			BiConsumer<SnomedComponent, Relationship> relationshipConsumer) throws ConversionException {

		AtomicReference<ConversionException> exceptionHolder = new AtomicReference<>();// Used to hold exceptions thrown within the lambda function
		changedAxioms.forEachRemaining(axiomMember -> {
			try {
				SAxiomRepresentation sAxiomRepresentation = axiomConversionService.convertAxiomMemberToAxiomRepresentation(axiomMember);
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
						.forEach(relationship -> relationshipConsumer.accept(axiomMember, relationship));
			} catch (ConversionException e) {
				exceptionHolder.set(e);
			}
		});
		if (exceptionHolder.get() != null) {
			throw exceptionHolder.get();
		}
	}

	private void applyAttributeChanges(QueryConcept queryConcept, Long conceptId, Map<Long, AttributeChanges> conceptAttributeChanges) {
		AttributeChanges attributeChanges = conceptAttributeChanges.get(conceptId);
		if (attributeChanges != null) {
			attributeChanges.getEffectiveSortedChanges().forEach(attributeChange -> {
				if (attributeChange.isAdd()) {
					queryConcept.addAttribute(attributeChange.getGroup(), attributeChange.getType(), attributeChange.getValue());
				} else {
					queryConcept.removeAttribute(attributeChange.getGroup(), attributeChange.getType(), attributeChange.getValue());
				}
			});
		}
	}

	private void removeQConceptChangesOnBranch(Commit commit) {
		// End versions on branch
		Branch branch = commit.getBranch();
		BranchCriteria branchCriteria = versionControlHelper.getChangesOnBranchCriteria(branch.getPath());
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
				.withPageable(LARGE_PAGE);
		try (CloseableIterator<QueryConcept> stream = elasticsearchTemplate.stream(queryBuilder.build(), QueryConcept.class)) {
			List<QueryConcept> deletionBatch = new ArrayList<>();
			stream.forEachRemaining(queryConcept -> {
				queryConcept.markDeleted();
				deletionBatch.add(queryConcept);
				if (deletionBatch.size() == BATCH_SAVE_SIZE) {
					deleteBatch(commit, deletionBatch);
					deletionBatch.clear();
				}
			});
			if (!deletionBatch.isEmpty()) {
				deleteBatch(commit, deletionBatch);
			}
		}

		// Restore versions from parent branches which were ended on this branch
		// This query is necessary because we don't know which of the ended versions are of type QueryConcept
		Collection<String> parentPaths = getParentPaths(branch.getPath());
		NativeSearchQueryBuilder endedVersionsQuery = new NativeSearchQueryBuilder()
				.withQuery(termsQuery("path", parentPaths))
				.withFilter(termsQuery("_id", branch.getVersionsReplaced(QueryConcept.class)))
				.withPageable(LARGE_PAGE);
		try (CloseableIterator<QueryConcept> stream = elasticsearchTemplate.stream(endedVersionsQuery.build(), QueryConcept.class)) {
			Set<String> queryConceptVersionsEnded = new HashSet<>();
			stream.forEachRemaining(c -> queryConceptVersionsEnded.add(c.getInternalId()));
			branch.getVersionsReplaced(QueryConcept.class).removeAll(queryConceptVersionsEnded);
			logger.info("Restored visibility of {} query concepts from parents", queryConceptVersionsEnded.size());
		}
	}

	Collection<String> getParentPaths(String path) {
		List<String> parents = new ArrayList<>();
		while ((path = PathUtil.getParentPath(path)) != null) {
			parents.add(path);
		}
		return parents;
	}

	private void deleteBatch(Commit commit, List<QueryConcept> deletionBatch) {
		logger.info("Ending {} query concepts", deletionBatch.size());
		doSaveBatchComponents(deletionBatch, commit, "conceptIdForm", queryConceptRepository);
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
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true)))
				.withFilter(termsQuery(Concept.Fields.CONCEPT_ID, requiredActiveConcepts))
				.withPageable(PageRequest.of(0, 1));

		Page<Concept> concepts = elasticsearchTemplate.queryForPage(queryBuilder.build(), Concept.class);
		if (concepts.getTotalElements() == requiredActiveConcepts.size()) {
			return Collections.emptySet();
		}

		// Some concepts are missing - let's collect them

		// Update query to collect concept ids efficiently
		queryBuilder
				.withFields(Concept.Fields.CONCEPT_ID)// Trigger mapping optimisation
				.withPageable(LARGE_PAGE);

		Set<Long> missingConceptIds = new LongOpenHashSet(requiredActiveConcepts);
		try (CloseableIterator<Concept> stream = elasticsearchTemplate.stream(queryBuilder.build(), Concept.class)) {
			stream.forEachRemaining(concept -> missingConceptIds.remove(concept.getConceptIdAsLong()));
		}

		return missingConceptIds;
	}

	private static final class AttributeChanges {

		private static final Comparator<AttributeChange> comparator = Comparator
				.comparing(AttributeChange::getEffectiveTime)
				.thenComparing(AttributeChange::isAdd);

		private List<AttributeChange> changes;

		private AttributeChanges() {
			changes = new ArrayList<>();
		}

		private void addAttribute(Integer effectiveTime, int groupId, Long type, Long value) {
			changes.add(new AttributeChange(effectiveTime, groupId, type, value, true));
		}

		private void removeAttribute(Integer effectiveTime, int groupId, long type, long value) {
			changes.add(new AttributeChange(effectiveTime, groupId, type, value, false));
		}

		private List<AttributeChange> getEffectiveSortedChanges() {
			changes.sort(comparator);
			return changes;
		}

	}

	private static final class AttributeChange {

		private final boolean add;
		private final int effectiveTime;
		private final int group;
		private final long type;
		private final long value;

		private AttributeChange(Integer effectiveTime, int group, long type, long value, boolean add) {
			this.add = add;
			if (effectiveTime == null) {
				effectiveTime = 90000000;
			}
			this.effectiveTime = effectiveTime;
			this.group = group;
			this.type = type;
			this.value = value;
		}

		private boolean isAdd() {
			return add;
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
	}
}
