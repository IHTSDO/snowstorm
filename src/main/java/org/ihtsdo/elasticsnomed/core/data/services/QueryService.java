package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.repositories.QueryIndexConceptRepository;
import org.ihtsdo.elasticsnomed.core.data.services.transitiveclosure.GraphBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.transitiveclosure.Node;
import org.ihtsdo.elasticsnomed.core.util.TimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class QueryService extends ComponentService {

	private static final int BATCH_SAVE_SIZE = 10000;
	public static final PageRequest PAGE_OF_ONE = new PageRequest(0, 1);

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private QueryIndexConceptRepository queryIndexConceptRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Collection<ConceptMini> search(ConceptQueryBuilder conceptQuery, String branchPath) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		String term = conceptQuery.getTermPrefix();
		if (term != null && term.length() < 3) {
			return Collections.emptySet();
		}

		final List<Long> termConceptIds = new LongArrayList();
		if (term != null) {
			logger.info("Lexical search");
			// Search for descriptions matching the term prefix
			BoolQueryBuilder boolQueryBuilder = boolQuery();
			NativeSearchQueryBuilder termSearchQuery = new NativeSearchQueryBuilder()
					.withQuery(boolQueryBuilder
							.must(branchCriteria)
							.must(termQuery("active", true))
							.must(termQuery("typeId", Concepts.FSN))
					);

			DescriptionService.addTermClauses(term, boolQueryBuilder);

			NativeSearchQuery query = termSearchQuery.build();
			DescriptionService.addTermSort(query);

			if (conceptQuery.hasLogicalConditions()) {
				try (CloseableIterator<Description> stream = elasticsearchTemplate.stream(query, Description.class)) {
					stream.forEachRemaining(d -> termConceptIds.add(parseLong(d.getConceptId())));
				}
			} else {
				termSearchQuery.withPageable(new PageRequest(0, 50));
				List<Description> descriptions = elasticsearchTemplate.queryForPage(query, Description.class).getContent();
				descriptions.forEach(d -> termConceptIds.add(parseLong(d.getConceptId())));
				logger.info("Gather minis");
				return conceptService.findConceptMinis(branchCriteria, termConceptIds).values();
			}

			logger.info("First term concept {}", !termConceptIds.isEmpty() ? termConceptIds.get(0) : null);
		}

		if (conceptQuery.hasLogicalConditions()) {
			logger.info("Logical search");
			NativeSearchQueryBuilder logicalSearchQuery = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteria)
							.must(conceptQuery.getRootBuilder())
					)
					.withPageable(new PageRequest(0, 50));

			if (term != null) {
				List<Long> values = termConceptIds.subList(0, termConceptIds.size() >= 50 ? 50 : termConceptIds.size());
				logger.info("logical filter size {} - {}", values.size(), values);
				logicalSearchQuery.withFilter(boolQuery().must(termsQuery("conceptId", values)));
			}

			Page<QueryConcept> queryConcepts = elasticsearchTemplate.queryForPage(logicalSearchQuery.build(), QueryConcept.class);
			List<Long> ids = queryConcepts.getContent().stream().map(QueryConcept::getConceptId).collect(Collectors.toList());

			logger.info("logical ids size {} - {}", ids.size(), ids);

			logger.info("Gather minis");
			Map<String, ConceptMini> conceptMiniMap = conceptService.findConceptMinis(branchCriteria, ids);

			if (term != null) {
				// Recreate term score ordering
				List<ConceptMini> minis = new ArrayList<>();
				for (Long termConceptId : termConceptIds) {
					ConceptMini conceptMini = conceptMiniMap.get(termConceptId.toString());
					if (conceptMini != null) {
						minis.add(conceptMini);
					}
				}
				return minis;
			}

			return conceptMiniMap.values();
		}
		return Collections.emptySet();
	}

	public CloseableIterator<QueryConcept> stream(NativeSearchQuery searchQuery) {
		return elasticsearchTemplate.stream(searchQuery, QueryConcept.class);
	}

	public Set<Long> retrieveAncestors(String conceptId, String path, boolean stated) {
		return retrieveAncestors(versionControlHelper.getBranchCriteria(path), path, stated, conceptId);
	}

	public Set<Long> retrieveParents(QueryBuilder branchCriteria, String path, boolean stated, String conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termQuery("conceptId", conceptId))
						.must(termQuery("stated", stated))
				)
				.withPageable(PAGE_OF_ONE)
				.build();
		List<QueryConcept> concepts = elasticsearchTemplate.queryForList(searchQuery, QueryConcept.class);
		return concepts.isEmpty() ? Collections.emptySet() : concepts.get(0).getParents();
	}

	public Set<Long> retrieveAncestors(QueryBuilder branchCriteria, String path, boolean stated, String conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termQuery("conceptId", conceptId))
						.must(termQuery("stated", stated))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> concepts = elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class).getContent();
		if (concepts.size() > 1) {
			logger.error("More than one index concept found {}", concepts);
			throw new IllegalStateException("More than one query-index-concept found for id " + conceptId + " on branch " + path + ".");
		}
		if (concepts.isEmpty()) {
			throw new IllegalArgumentException(String.format("Concept %s not found on branch %s", conceptId, path));
		}
		return concepts.get(0).getAncestors();
	}

	public Set<Long> retrieveAllAncestors(QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termsQuery("conceptId", conceptId))
						.must(termQuery("stated", stated))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> concepts = elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class).getContent();
		Set<Long> allAncestors = new HashSet<>();
		for (QueryConcept concept : concepts) {
			allAncestors.addAll(concept.getAncestors());
		}
		return allAncestors;
	}

	public Set<Long> retrieveDescendants(String conceptId, QueryBuilder branchCriteria, boolean stated) {
		return retrieveAllDescendants(branchCriteria, stated, Collections.singleton(conceptId));
	}

	public Set<Long> retrieveAllDescendants(QueryBuilder branchCriteria, boolean stated, Collection<? extends Object> conceptIds) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termsQuery("ancestors", conceptIds))
						.must(termQuery("stated", stated))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> concepts = elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class).getContent();
		return concepts.stream().map(QueryConcept::getConceptId).collect(Collectors.toSet());
	}

	public void rebuildStatedAndInferredTransitiveClosures(String branch) {
		// TODO: Only use on MAIN
		try (Commit commit = branchService.openCommit(branch)) {
			QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(commit.getBranch());
			updateTransitiveClosure(true, branchCriteria, Collections.emptySet(), commit, true);
			updateTransitiveClosure(false, branchCriteria, Collections.emptySet(), commit, true);
			commit.markSuccessful();
		}
	}

	void updateStatedAndInferredTransitiveClosures(Commit commit) {
		if (commit.isRebase()) {
			// Recreate query index using new parent base point + content on this branch
			Branch branch = commit.getBranch();
			String fatPath = branch.getFatPath();
			removeQConceptChangesOnBranch(commit, branch, fatPath);

			Page<QueryConcept> page = elasticsearchTemplate.queryForPage(new NativeSearchQueryBuilder().withQuery(versionControlHelper.getChangesOnBranchCriteria(branch)).build(), QueryConcept.class);
			System.out.println("total QueryConcept on path = " + page.getTotalElements());

			QueryBuilder changesBranchCriteria = versionControlHelper.getChangesOnBranchCriteria(branch);
			Set<String> parentVersionsReplacedOnBranch = branch.getVersionsReplaced();
			updateTransitiveClosure(true, changesBranchCriteria, parentVersionsReplacedOnBranch, commit, false);
			updateTransitiveClosure(false, changesBranchCriteria, parentVersionsReplacedOnBranch, commit, false);
		} else {
			// Update query index using changes in the last commit
			QueryBuilder changesBranchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
			Set<String> parentVersionsReplacedDuringCommit = commit.getEntityVersionsDeleted();
			updateTransitiveClosure(true, changesBranchCriteria, parentVersionsReplacedDuringCommit, commit, false);
			updateTransitiveClosure(false, changesBranchCriteria, parentVersionsReplacedDuringCommit, commit, false);
		}
	}

	private void updateTransitiveClosure(boolean stated, QueryBuilder changesBranchCriteria, Set<String> deletionsToProcess, Commit commit, boolean rebuild) {
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
		}

		logger.info("Performing {} of {} transitive closures", rebuild ? "rebuild" : "incremental update", formName);

		TimerUtil timer = new TimerUtil("TC index " + formName);
		Set<Long> updateSource = new HashSet<>();
		Set<Long> updateDestination = new HashSet<>();
		Set<Long> existingAncestors = new HashSet<>();
		Set<Long> existingDescendants = new HashSet<>();

		String committedContentPath = commit.getBranch().getFatPath();
		if (commit.isRebase()) {
			// When rebasing this is a view onto the new parent state
			committedContentPath = PathUtil.getParentPath(committedContentPath);
		}
		QueryBuilder branchCriteriaForAlreadyCommittedContent = versionControlHelper.getBranchCriteria(committedContentPath);
		timer.checkpoint("get branch criteria");
		if (!rebuild) {
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
				logger.info("No TC {} changes found. Nothing to do.", formName);
				return;
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
							.must(termsQuery("conceptId", Sets.union(updateSource, updateDestination))))
					.withPageable(ConceptService.LARGE_PAGE).build();
			try (final CloseableIterator<QueryConcept> existingIndexConcepts = elasticsearchTemplate.stream(query, QueryConcept.class)) {
				existingIndexConcepts.forEachRemaining(indexConcept -> {
					existingAncestors.addAll(indexConcept.getAncestors());
				});
			}
			timer.checkpoint("Collect existingAncestors from QueryConcept.");

			// Step: Identify existing descendants
			// Strategy: Find existing nodes where TC matches updated relationship source ids
			try (final CloseableIterator<QueryConcept> existingIndexConcepts = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteriaForAlreadyCommittedContent)
							.must(termsQuery("stated", stated))
					)
					.withFilter(boolQuery()
							.must(termsQuery("ancestors", updateSource)))
					.withPageable(ConceptService.LARGE_PAGE).build(), QueryConcept.class)) {
				existingIndexConcepts.forEachRemaining(indexConcept -> existingDescendants.add(indexConcept.getConceptId()));
			}
			timer.checkpoint("Collect existingDescendants from QueryConcept.");

			logger.info("{} existing ancestors and {} existing descendants of updated relationships identified.", existingAncestors.size(), existingDescendants.size());
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
		try (final CloseableIterator<Relationship> existingInferredIsARelationships = elasticsearchTemplate.stream(queryBuilder.build(), Relationship.class)) {
			existingInferredIsARelationships.forEachRemaining(relationship -> {
				graphBuilder.addParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()));
			});
		}
		timer.checkpoint("Build existing nodes from Relationships.");
		logger.info("{} existing nodes loaded.", graphBuilder.getNodeCount());


		// Step - Update graph
		// Strategy: Add/remove edges from new commit
		AtomicLong relationshipsAdded = new AtomicLong();
		AtomicLong relationshipsRemoved = new AtomicLong();
		boolean newGraph = graphBuilder.getNodeCount() == 0;
		try (final CloseableIterator<Relationship> newInferredIsARelationships = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(changesBranchCriteria)
						.must(termQuery("typeId", Concepts.ISA))
						.must(termsQuery("characteristicTypeId", characteristicTypeIds))
				)
				.withSort(new FieldSortBuilder("start").order(SortOrder.ASC))
				.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
			newInferredIsARelationships.forEachRemaining(relationship -> {
				boolean ignore = false;
				boolean justDeleted = false;
				if (relationship.getEnd() != null) {
					if (deletionsToProcess.contains(relationship.getId())) {
						justDeleted = true;
					} else {
						ignore = true;
					}
				}
				if (!ignore) {
					if (!justDeleted && relationship.isActive()){
						graphBuilder.addParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()))
								.markUpdated();
						relationshipsAdded.incrementAndGet();
					} else{
						Node node = graphBuilder.removeParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()));
						if (node != null) {
							node.markUpdated();
						}
						relationshipsRemoved.incrementAndGet();
					}
				}
			});
		}
		timer.checkpoint("Update graph using changed Relationships.");
		logger.info("{} {} is-a relationships added, {} removed.", relationshipsAdded.get(), formName, relationshipsRemoved.get());


		// Step: Save changes
		Set<QueryConcept> indexConceptsToSave = new HashSet<>();
		graphBuilder.getNodes().forEach(node -> {
			if (newGraph || node.isAncestorOrSelfUpdated()) {
				final Set<Long> transitiveClosure = node.getTransitiveClosure();
				final Set<Long> parentIds = node.getParents().stream().map(Node::getId).collect(Collectors.toSet());
				final Long nodeId = node.getId();
				indexConceptsToSave.add(new QueryConcept(nodeId, parentIds, transitiveClosure, stated));
			}
		});
		if (!indexConceptsToSave.isEmpty()) {
			for (List<QueryConcept> queryConcepts : Iterables.partition(indexConceptsToSave, BATCH_SAVE_SIZE)) {
				doSaveBatch(queryConcepts, commit);
			}
		}
		timer.checkpoint("Save updated QueryIndexConcepts");
		logger.info("{} {} concept transitive closures updated.", indexConceptsToSave.size(), formName);

		timer.finish();
	}

	private void removeQConceptChangesOnBranch(Commit commit, Branch branch, String fatPath) {
		// End versions on branch
		QueryBuilder branchCriteria = versionControlHelper.getChangesOnBranchCriteria(fatPath);
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
		NativeSearchQueryBuilder endedVersionsQuery = new NativeSearchQueryBuilder()
				.withQuery(termsQuery("_id", branch.getVersionsReplaced()))
				.withPageable(LARGE_PAGE);
		try (CloseableIterator<QueryConcept> stream = elasticsearchTemplate.stream(endedVersionsQuery.build(), QueryConcept.class)) {
			Set<String> queryConceptVersionsEnded = new HashSet<>();
			stream.forEachRemaining(c -> queryConceptVersionsEnded.add(c.getInternalId()));
			branch.getVersionsReplaced().removeAll(queryConceptVersionsEnded);
			logger.info("Restored visibility of {} query concepts from parents", queryConceptVersionsEnded.size());
		}
	}

	private Iterable<QueryConcept> deleteBatch(Commit commit, List<QueryConcept> deletionBatch) {
		logger.info("Ending {} query concepts", deletionBatch.size());
		return doSaveBatchComponents(deletionBatch, commit, "conceptIdForm", queryIndexConceptRepository);
	}

	private void doSaveBatch(Collection<QueryConcept> indexConcepts, Commit commit) {
		doSaveBatchComponents(indexConcepts, commit, "conceptIdForm", queryIndexConceptRepository);
	}

	public void deleteAll() {
		queryIndexConceptRepository.deleteAll();
	}

	/**
	 * Creates a ConceptQueryBuilder for use with search methods.
	 * @param stated If the stated or inferred form should be used in any logical conditions.
	 * @return a new ConceptQueryBuilder
	 */
	public ConceptQueryBuilder createQueryBuilder(boolean stated) {
		return new ConceptQueryBuilder(stated);
	}

	public final class ConceptQueryBuilder {

		private final BoolQueryBuilder rootBuilder;
		private final BoolQueryBuilder logicalConditionBuilder;
		private String termPrefix;

		private ConceptQueryBuilder(boolean stated) {
			rootBuilder = boolQuery();
			logicalConditionBuilder = boolQuery();
			rootBuilder.must(termQuery("stated", stated));
			rootBuilder.must(logicalConditionBuilder);
		}

		public void self(Long conceptId) {
			logger.info("conceptId = {}", conceptId);
			logicalConditionBuilder.should(termQuery("conceptId", conceptId));
		}

		public void descendant(Long conceptId) {
			logger.info("ancestors = {}", conceptId);
			logicalConditionBuilder.should(termQuery("ancestors", conceptId));
		}

		public void selfOrDescendant(Long conceptId) {
			self(conceptId);
			descendant(conceptId);
		}

		/**
		 * Term prefix has a minimum length of 3 characters.
		 * @param termPrefix
		 */
		public void termPrefix(String termPrefix) {
			this.termPrefix = termPrefix;
		}

		private BoolQueryBuilder getRootBuilder() {
			return rootBuilder;
		}

		private String getTermPrefix() {
			return termPrefix;
		}

		private boolean hasLogicalConditions() {
			return logicalConditionBuilder.hasClauses();
		}
	}
}
