package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.repositories.QueryConceptRepository;
import org.ihtsdo.elasticsnomed.core.data.services.transitiveclosure.GraphBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.transitiveclosure.Node;
import org.ihtsdo.elasticsnomed.core.util.TimerUtil;
import org.ihtsdo.elasticsnomed.ecl.ECLQueryService;
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
	private QueryConceptRepository queryConceptRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ECLQueryService eclQueryService;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private static final long IS_A_TYPE = parseLong(Concepts.ISA);

	public List<ConceptMini> search(ConceptQueryBuilder conceptQuery, String branchPath) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		String term = conceptQuery.getTermPrefix();
		if (term != null && term.length() < 3) {
			return Collections.emptyList();
		}

		final List<Long> termConceptIds = new LongArrayList();
		if (term != null) {
			logger.info("Lexical search {}", term);
			// Search for descriptions matching the term prefix
			BoolQueryBuilder boolQueryBuilder = boolQuery()
					.must(branchCriteria)
					.must(termQuery("active", true))
					.must(termQuery("typeId", Concepts.FSN));

			DescriptionService.addTermClauses(term, boolQueryBuilder);

			if (conceptQuery.hasLogicalConditions()) {
				// Page through all matches.
				// Stream can not be used because there is no ordering.
				Page<Description> page;
				Integer offset = 0;
				int pageSize = LARGE_PAGE.getPageSize();
				do {
					NativeSearchQuery query = new NativeSearchQueryBuilder()
							.withQuery(boolQueryBuilder)
							.withPageable(new PageRequest(offset, pageSize))
							.build();
					DescriptionService.addTermSort(query);

					page = elasticsearchTemplate.queryForPage(query, Description.class);
					termConceptIds.addAll(page.getContent().stream().map(d -> parseLong(d.getConceptId())).collect(Collectors.toList()));
					offset += pageSize;
				} while (!page.isLast());
			} else {
				NativeSearchQuery query = new NativeSearchQueryBuilder()
						.withQuery(boolQueryBuilder)
						.withPageable(new PageRequest(0, 50))
						.build();
				DescriptionService.addTermSort(query);

				List<Description> descriptions = elasticsearchTemplate.queryForPage(query, Description.class).getContent();
				descriptions.forEach(d -> termConceptIds.add(parseLong(d.getConceptId())));
				logger.info("Gather minis");
				Map<String, ConceptMini> conceptMiniMap = conceptService.findConceptMinis(branchCriteria, termConceptIds);
				return getConceptList(termConceptIds, conceptMiniMap);
			}
		}

		if (conceptQuery.hasLogicalConditions()) {
			String ecl = conceptQuery.getEcl();

			// TODO: Pagination
			PageRequest pageRequest = new PageRequest(0, 50);

			Collection<Long> pageOfMatchIds;
			if (ecl != null) {
				logger.info("ECL Search {}", ecl);
				pageOfMatchIds = eclQueryService.selectConceptIds(ecl, branchCriteria, branchPath, conceptQuery.isStated(),
						term != null ? termConceptIds : null, pageRequest);
			} else {
				logger.info("Logical Search");
				NativeSearchQueryBuilder logicalSearchQuery = new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(branchCriteria)
								.must(conceptQuery.getRootBuilder())
						)
						.withPageable(pageRequest);
				if (term != null) {
					logicalSearchQuery.withFilter(boolQuery().must(termsQuery("conceptId", termConceptIds)));
				}
				Page<QueryConcept> queryConcepts = elasticsearchTemplate.queryForPage(logicalSearchQuery.build(), QueryConcept.class);
				pageOfMatchIds = queryConcepts.getContent().stream().map(QueryConcept::getConceptId).collect(Collectors.toList());
			}

			logger.info("logical ids size {}", pageOfMatchIds.size());

			logger.info("Gather minis");
			Map<String, ConceptMini> conceptMiniMap = conceptService.findConceptMinis(branchCriteria, pageOfMatchIds);

			if (term != null) {
				// Recreate term score ordering
				return getConceptList(termConceptIds, conceptMiniMap);
			}

			return new ArrayList<>(conceptMiniMap.values());
		}
		return Collections.emptyList();
	}

	private List<ConceptMini> getConceptList(List<Long> termConceptIds, Map<String, ConceptMini> conceptMiniMap) {
		return termConceptIds.stream().filter(id -> conceptMiniMap.keySet().contains(id.toString())).map(id -> conceptMiniMap.get(id.toString())).collect(Collectors.toList());
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
			removeQConceptChangesOnBranch(commit);

			Page<QueryConcept> page = elasticsearchTemplate.queryForPage(new NativeSearchQueryBuilder().withQuery(versionControlHelper.getChangesOnBranchCriteria(branch)).build(), QueryConcept.class);
			System.out.println("total QueryConcept on path = " + page.getTotalElements());

			QueryBuilder changesBranchCriteria = versionControlHelper.getChangesOnBranchCriteria(branch);
			Set<String> deletedComponents = branch.getVersionsReplaced();
			updateTransitiveClosure(true, changesBranchCriteria, deletedComponents, commit, false);
			updateTransitiveClosure(false, changesBranchCriteria, deletedComponents, commit, false);
		} else {
			// Update query index using changes in the last commit
			QueryBuilder changesBranchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
			Set<String> deletedComponents = commit.getEntityVersionsDeleted();
			updateTransitiveClosure(true, changesBranchCriteria, deletedComponents, commit, false);
			updateTransitiveClosure(false, changesBranchCriteria, deletedComponents, commit, false);
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

		String committedContentPath = commit.getBranch().getPath();
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
			try (final CloseableIterator<QueryConcept> existingQueryConcepts = elasticsearchTemplate.stream(query, QueryConcept.class)) {
				existingQueryConcepts.forEachRemaining(queryConcept -> {
					existingAncestors.addAll(queryConcept.getAncestors());
				});
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
				existingQueryConcepts.forEachRemaining(queryConcept -> existingDescendants.add(queryConcept.getConceptId()));
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
			existingInferredIsARelationships.forEachRemaining(relationship ->
					graphBuilder.addParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId())));
		}
		timer.checkpoint("Build existing nodes from Relationships.");
		logger.info("{} existing nodes loaded.", graphBuilder.getNodeCount());


		// Step - Update graph
		// Strategy: Add/remove edges from new commit
		// Also collect other attribute changes
		AtomicLong isARelationshipsAdded = new AtomicLong();
		AtomicLong isARelationshipsRemoved = new AtomicLong();
		boolean newGraph = graphBuilder.getNodeCount() == 0;
		Map<Long, AttributeChanges> conceptAttributeChanges = new Long2ObjectOpenHashMap<>();
		try (final CloseableIterator<Relationship> inferredIsARelationshipChanges = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(changesBranchCriteria)
						.must(termsQuery("characteristicTypeId", characteristicTypeIds))
				)
				.withSort(new FieldSortBuilder("start").order(SortOrder.ASC))
				.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
			inferredIsARelationshipChanges.forEachRemaining(relationship -> {
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
					if (!justDeleted && relationship.isActive()) {
						if (type == IS_A_TYPE) {
							graphBuilder.addParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()))
									.markUpdated();
							isARelationshipsAdded.incrementAndGet();
						} else {
							conceptAttributeChanges.computeIfAbsent(conceptId, (c) -> new AttributeChanges()).addAttribute(groupId, type, value);
						}
					} else {
						if (type == IS_A_TYPE) {
							Node node = graphBuilder.removeParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()));
							if (node != null) {
								node.markUpdated();
							}
							isARelationshipsRemoved.incrementAndGet();
						} else {
							conceptAttributeChanges.computeIfAbsent(conceptId, (c) -> new AttributeChanges()).removeAttribute(groupId, type, value);
						}
					}
				}
			});
		}
		timer.checkpoint("Update graph using changed relationships.");
		logger.info("{} {} is-a relationships added, {} removed.", isARelationshipsAdded.get(), formName, isARelationshipsRemoved.get());

		// Step: Save changes
		Map<Long, Node> nodesToSave = new Long2ObjectOpenHashMap<>();
		graphBuilder.getNodes().stream()
				.filter(node -> newGraph || node.isAncestorOrSelfUpdated() || conceptAttributeChanges.containsKey(node.getId()))
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
				Long conceptId = queryConcept.getConceptId();
				Node node = nodesToSave.get(conceptId);
				queryConcept.setParents(node.getParents().stream().map(Node::getId).collect(Collectors.toSet()));
				queryConcept.setAncestors(node.getTransitiveClosure());
				applyAttributeChanges(queryConcept, conceptId, conceptAttributeChanges);
				queryConceptsToSave.add(queryConcept);
				nodesNotFound.remove(conceptId);
			});
		}

		timer.checkpoint("Collect existingDescendants from QueryConcept.");

		nodesNotFound.forEach(nodeId -> {
			Node node = nodesToSave.get(nodeId);
			final Set<Long> transitiveClosure = node.getTransitiveClosure();
			final Set<Long> parentIds = node.getParents().stream().map(Node::getId).collect(Collectors.toSet());
			QueryConcept queryConcept = new QueryConcept(nodeId, parentIds, transitiveClosure, stated);
			applyAttributeChanges(queryConcept, nodeId, conceptAttributeChanges);
			queryConceptsToSave.add(queryConcept);
		});
		if (!queryConceptsToSave.isEmpty()) {
			for (List<QueryConcept> queryConcepts : Iterables.partition(queryConceptsToSave, BATCH_SAVE_SIZE)) {
				doSaveBatch(queryConcepts, commit);
			}
		}
		timer.checkpoint("Save updated QueryConcepts");
		logger.info("{} {} concept transitive closures updated.", queryConceptsToSave.size(), formName);

		timer.finish();
	}

	private void applyAttributeChanges(QueryConcept queryConcept, Long conceptId, Map<Long, AttributeChanges> conceptAttributeChanges) {
		AttributeChanges attributeChanges = conceptAttributeChanges.get(conceptId);
		if (attributeChanges != null) {
			attributeChanges.getAdd().forEach((attribute) -> {
				queryConcept.addAttribute(attribute.getGroup(), attribute.getType(), attribute.getValue());
			});
			attributeChanges.getRemove().forEach((attribute) -> {
				queryConcept.removeAttribute(attribute.getGroup(), attribute.getType(), attribute.getValue());
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
		return doSaveBatchComponents(deletionBatch, commit, "conceptIdForm", queryConceptRepository);
	}

	private void doSaveBatch(Collection<QueryConcept> queryConcepts, Commit commit) {
		doSaveBatchComponents(queryConcepts, commit, "conceptIdForm", queryConceptRepository);
	}

	public void deleteAll() {
		queryConceptRepository.deleteAll();
	}

	/**
	 * Creates a ConceptQueryBuilder for use with search methods.
	 *
	 * @param stated If the stated or inferred form should be used in any logical conditions.
	 * @return a new ConceptQueryBuilder
	 */
	public ConceptQueryBuilder createQueryBuilder(boolean stated) {
		return new ConceptQueryBuilder(stated);
	}

	public final class ConceptQueryBuilder {

		private final BoolQueryBuilder rootBuilder;
		private final BoolQueryBuilder logicalConditionBuilder;
		private final boolean stated;
		private String termPrefix;
		private String ecl;

		private ConceptQueryBuilder(boolean stated) {
			this.stated = stated;
			rootBuilder = boolQuery();
			logicalConditionBuilder = boolQuery();
			rootBuilder.must(termQuery("stated", stated));
			rootBuilder.must(logicalConditionBuilder);
		}

		public ConceptQueryBuilder self(Long conceptId) {
			logger.info("conceptId = {}", conceptId);
			logicalConditionBuilder.should(termQuery("conceptId", conceptId));
			return this;
		}

		public ConceptQueryBuilder descendant(Long conceptId) {
			logger.info("ancestors = {}", conceptId);
			logicalConditionBuilder.should(termQuery("ancestors", conceptId));
			return this;
		}

		public ConceptQueryBuilder selfOrDescendant(Long conceptId) {
			self(conceptId);
			descendant(conceptId);
			return this;
		}

		public ConceptQueryBuilder ecl(String ecl) {
			this.ecl = ecl;
			return this;
		}

		/**
		 * Term prefix has a minimum length of 3 characters.
		 *
		 * @param termPrefix
		 */
		public ConceptQueryBuilder termPrefix(String termPrefix) {
			this.termPrefix = termPrefix;
			return this;
		}

		private BoolQueryBuilder getRootBuilder() {
			return rootBuilder;
		}

		private String getTermPrefix() {
			return termPrefix;
		}

		public String getEcl() {
			return ecl;
		}

		public boolean isStated() {
			return stated;
		}

		private boolean hasLogicalConditions() {
			return getEcl() != null || logicalConditionBuilder.hasClauses();
		}
	}

	private static final class AttributeChanges {

		private Set<Attribute> add;
		private Set<Attribute> remove;

		private AttributeChanges() {
			add = new HashSet<>();
			remove = new HashSet<>();
		}

		private void addAttribute(int groupId, Long type, Long value) {
			add.add(new Attribute(groupId, type, value));
		}

		private void removeAttribute(int groupId, Long type, Long value) {
			remove.add(new Attribute(groupId, type, value));
		}

		private Set<Attribute> getAdd() {
			return add;
		}

		private Set<Attribute> getRemove() {
			return remove;
		}
	}

	private static final class Attribute {
		private int group;
		private Long type;
		private Long value;

		private Attribute(int group, Long type, Long value) {
			this.group = group;
			this.type = type;
			this.value = value;
		}

		private int getGroup() {
			return group;
		}

		private Long getType() {
			return type;
		}

		private Long getValue() {
			return value;
		}
	}
}