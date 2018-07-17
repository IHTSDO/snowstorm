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
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.QueryConceptRepository;
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
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class QueryConceptUpdateService extends ComponentService implements CommitListener {

	@Value("${commit-hook.semantic-indexing.enabled:true}")
	private boolean semanticIndexingEnabled;

	protected static final int BATCH_SAVE_SIZE = 10000;
	private static final long IS_A_TYPE = parseLong(Concepts.ISA);

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private QueryConceptRepository queryConceptRepository;

	@Autowired
	private BranchService branchService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		branchService.addCommitListener(this);
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (semanticIndexingEnabled) {
			updateStatedAndInferredSemanticIndex(commit);
		} else {
			logger.info("Semantic indexing is disabled.");
		}
	}

	public void rebuildStatedAndInferredSemanticIndex(String branch) {
		// TODO: Only use on MAIN
		try (Commit commit = branchService.openCommit(branch)) {
			QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(commit.getBranch());
			updateSemanticIndex(true, branchCriteria, Collections.emptySet(), commit, true);
			updateSemanticIndex(false, branchCriteria, Collections.emptySet(), commit, true);
			commit.markSuccessful();
		}
	}

	void updateStatedAndInferredSemanticIndex(Commit commit) throws IllegalStateException {
		if (commit.isRebase()) {
			// Recreate query index using new parent base point + content on this branch
			Branch branch = commit.getBranch();
			removeQConceptChangesOnBranch(commit);

			Page<QueryConcept> page = elasticsearchTemplate.queryForPage(new NativeSearchQueryBuilder().withQuery(versionControlHelper.getChangesOnBranchCriteria(branch)).build(), QueryConcept.class);
			System.out.println("total QueryConcept on path = " + page.getTotalElements());

			QueryBuilder changesBranchCriteria = versionControlHelper.getChangesOnBranchCriteria(branch);
			Set<String> deletedComponents = branch.getVersionsReplaced();
			updateSemanticIndex(true, changesBranchCriteria, deletedComponents, commit, false);
			updateSemanticIndex(false, changesBranchCriteria, deletedComponents, commit, false);
		} else {
			// Update query index using changes in the last commit
			QueryBuilder changesBranchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
			Set<String> deletedComponents = commit.getEntityVersionsDeleted();
			updateSemanticIndex(true, changesBranchCriteria, deletedComponents, commit, false);
			updateSemanticIndex(false, changesBranchCriteria, deletedComponents, commit, false);
		}
	}

	private void updateSemanticIndex(boolean stated, QueryBuilder changesBranchCriteria, Set<String> deletionsToProcess, Commit commit, boolean rebuild) throws IllegalStateException {
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

		TimerUtil timer = new TimerUtil("TC index " + formName, Level.DEBUG);
		Set<Long> updateSource = new HashSet<>();
		Set<Long> updateDestination = new HashSet<>();
		Set<Long> existingAncestors = new HashSet<>();
		Set<Long> existingDescendants = new HashSet<>();

		String branchPath = commit.getBranch().getPath();
		String committedContentPath = branchPath;
		if (commit.isRebase()) {
			// When rebasing this is a view onto the new parent state
			committedContentPath = PathUtil.getParentPath(committedContentPath);
			// TODO: Check history to establish what this unused variable was used then fix or remove.
		}
		QueryBuilder branchCriteriaForAlreadyCommittedContent = versionControlHelper.getBranchCriteriaBeforeOpenCommit(commit);
		timer.checkpoint("get branch criteria");
		if (rebuild) {
			logger.info("Performing {} of {} semantic index", "rebuild", formName);
		} else {
			// Step: Collect source and destinations of changed is-a relationships
			try (final CloseableIterator<Relationship> changedIsARelationships = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(changesBranchCriteria)
							.must(termQuery("typeId", Concepts.ISA))
							.must(termsQuery("characteristicTypeId", characteristicTypeIds))
					)
					.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
				changedIsARelationships.forEachRemaining(relationship -> {
					updateSource.add(parseLong(relationship.getSourceId()));
					updateDestination.add(parseLong(relationship.getDestinationId()));
				});
			}
			timer.checkpoint("Collect changed relationships.");

			if (updateSource.isEmpty()) {
				return;
			} else {
				logger.info("Performing {} of {} semantic index", "incremental update", formName);
			}

			// Identify parts of the graph nodes are moving from or to

			// Step: Identify existing TC of updated nodes
			// Strategy: Find existing nodes where ID matches updated relationship source or destination ids, record TC
			NativeSearchQuery query = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteriaForAlreadyCommittedContent)
							.must(termsQuery("stated", stated))
					)
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
							.must(branchCriteriaForAlreadyCommittedContent)
							.must(termsQuery("stated", stated))
					)
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
						.must(branchCriteriaForAlreadyCommittedContent)
						.must(termQuery("active", true))
						.must(termQuery("typeId", Concepts.ISA))
						.must(termsQuery("characteristicTypeId", characteristicTypeIds))
				)
				.withPageable(ConceptService.LARGE_PAGE);
		if (!rebuild) {
			Set<Long> nodesToLoad = new HashSet<>();
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
		logger.debug("{} existing nodes loaded.", graphBuilder.getNodeCount());


		// Step - Update graph
		// Strategy: Add/remove edges from new commit
		// Also collect other attribute changes
		AtomicLong relationshipsAdded = new AtomicLong();
		AtomicLong relationshipsRemoved = new AtomicLong();
		boolean newGraph = graphBuilder.getNodeCount() == 0;
		Set<Long> requiredActiveConcepts = new LongOpenHashSet();
		Map<Long, AttributeChanges> conceptAttributeChanges = new Long2ObjectOpenHashMap<>();
		try (final CloseableIterator<Relationship> relationshipChanges = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(changesBranchCriteria)
						.must(termsQuery("characteristicTypeId", characteristicTypeIds))
				)
				.withSort(SortBuilders.fieldSort(Relationship.Fields.EFFECTIVE_TIME))
				.withSort(SortBuilders.fieldSort(Relationship.Fields.ACTIVE))
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
			relationshipChanges.forEachRemaining(relationship -> {
				boolean ignore = false;
				boolean justDeleted = false;
				if (relationship.getEnd() != null) {
					if (deletionsToProcess.contains(relationship.getId())) {
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
					String effectiveTime = relationship.getEffectiveTime();
					if (!justDeleted && relationship.isActive()) {
						if (type == IS_A_TYPE) {
							graphBuilder.addParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()))
									.markUpdated();
							relationshipsAdded.incrementAndGet();
						} else {
							conceptAttributeChanges.computeIfAbsent(conceptId, (c) -> new AttributeChanges()).addAttribute(effectiveTime, groupId, type, value);
						}
						requiredActiveConcepts.add(conceptId);
						requiredActiveConcepts.add(type);
						requiredActiveConcepts.add(value);
					} else {
						if (type == IS_A_TYPE) {
							Node node = graphBuilder.removeParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()));
							if (node != null) {
								node.markUpdated();
							}
							relationshipsRemoved.incrementAndGet();
						} else {
							conceptAttributeChanges.computeIfAbsent(conceptId, (c) -> new AttributeChanges()).removeAttribute(effectiveTime, groupId, type, value);
						}
					}
				}
			});
		}
		timer.checkpoint("Update graph using changed relationships.");
		logger.debug("{} {} relationships added, {} inactive/removed.", relationshipsAdded.get(), formName, relationshipsRemoved.get());

		Set<Long> inactiveOrMissingConceptIds = getInactiveOrMissingConceptIds(requiredActiveConcepts, versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit));
		if (!inactiveOrMissingConceptIds.isEmpty()) {
			logger.error("The following concepts have been referred to in relationships but are missing or inactive: " + inactiveOrMissingConceptIds);

			// TODO: Should we throw an IllegalStateException and roll back the commit here?

//			throw new IllegalStateException("The following concepts have been referred to in relationships but are missing or inactive: " + inactiveOrMissingConceptIds);
		}

		// Step: Save changes
		Map<Long, Node> nodesToSave = new Long2ObjectOpenHashMap<>();
		graphBuilder.getNodes().stream()
				.filter(node -> newGraph || rebuild || node.isAncestorOrSelfUpdated(branchPath) || conceptAttributeChanges.containsKey(node.getId()))
				.forEach(node -> nodesToSave.put(node.getId(), node));
		Set<Long> nodesNotFound = new LongOpenHashSet(nodesToSave.keySet());
		Set<QueryConcept> queryConceptsToSave = new HashSet<>();
		try (final CloseableIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteriaForAlreadyCommittedContent)
						.must(termsQuery(QueryConcept.STATED_FIELD, stated))
				)
				.withFilter(boolQuery()
						.must(termsQuery(QueryConcept.CONCEPT_ID_FORM_FIELD, nodesToSave.values().stream().map(n -> QueryConcept.toConceptIdForm(n.getId(), stated)).collect(Collectors.toList()))))
				.withPageable(ConceptService.LARGE_PAGE).build(), QueryConcept.class)) {
			existingQueryConcepts.forEachRemaining(queryConcept -> {
				Long conceptId = queryConcept.getConceptIdL();
				Node node = nodesToSave.get(conceptId);
				queryConcept.setParents(node.getParents().stream().map(Node::getId).collect(Collectors.toSet()));
				queryConcept.setAncestors(node.getTransitiveClosure(branchPath));
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
		QueryBuilder branchCriteria = versionControlHelper.getChangesOnBranchCriteria(branch.getPath());
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(branchCriteria)
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
				.withFilter(termsQuery("_id", branch.getVersionsReplaced()))
				.withPageable(LARGE_PAGE);
		try (CloseableIterator<QueryConcept> stream = elasticsearchTemplate.stream(endedVersionsQuery.build(), QueryConcept.class)) {
			Set<String> queryConceptVersionsEnded = new HashSet<>();
			stream.forEachRemaining(c -> queryConceptVersionsEnded.add(c.getInternalId()));
			branch.getVersionsReplaced().removeAll(queryConceptVersionsEnded);
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

	private Iterable<QueryConcept> deleteBatch(Commit commit, List<QueryConcept> deletionBatch) {
		logger.info("Ending {} query concepts", deletionBatch.size());
		return doSaveBatchComponents(deletionBatch, commit, "conceptIdForm", queryConceptRepository);
	}

	private void doSaveBatch(Collection<QueryConcept> queryConcepts, Commit commit) {
		doSaveBatchComponents(queryConcepts, commit, "conceptIdForm", queryConceptRepository);
	}

	Set<Long> getInactiveOrMissingConceptIds(Set<Long> requiredActiveConcepts, QueryBuilder branchCriteria) {
		// We can't select the concepts which are not there!
		// For speed first we will count the concepts which are there and active
		// If the count doesn't match we load the ids of the concepts which are there so we can work out those which are not.

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
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

		private void addAttribute(String effectiveTime, int groupId, Long type, Long value) {
			changes.add(new AttributeChange(effectiveTime, groupId, type, value, true));
		}

		private void removeAttribute(String effectiveTime, int groupId, long type, long value) {
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

		private AttributeChange(String effectiveTime, int group, long type, long value, boolean add) {
			this.add = add;
			if (effectiveTime == null || effectiveTime.isEmpty()) {
				effectiveTime = "90000000";
			}
			this.effectiveTime = parseInt(effectiveTime);
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
