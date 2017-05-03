package org.ihtsdo.elasticsnomed.services;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.domain.Concepts;
import org.ihtsdo.elasticsnomed.domain.QueryIndexConcept;
import org.ihtsdo.elasticsnomed.domain.Relationship;
import org.ihtsdo.elasticsnomed.repositories.QueryIndexConceptRepository;
import org.ihtsdo.elasticsnomed.services.transitiveclosure.GraphBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
public class QueryIndexService extends ComponentService {

	public static final int BATCH_SAVE_SIZE = 5000;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private QueryIndexConceptRepository queryIndexConceptRepository;

	@Autowired
	private BranchService branchService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Set<Long> retrieveAncestors(String conceptId, String path, boolean stated) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteria(path))
						.must(termQuery("conceptId", conceptId))
						.must(termQuery("stated", stated))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryIndexConcept> concepts = elasticsearchTemplate.queryForPage(searchQuery, QueryIndexConcept.class).getContent();
		if (concepts.size() > 1) {
			logger.error("More than one index concept found {}", concepts);
			throw new IllegalStateException("More than one query-index-concept found for id " + conceptId + " on branch " + path + ".");
		}
		if (concepts.isEmpty()) {
			throw new IllegalArgumentException(String.format("Concept %s not found on branch %s", conceptId, path));
		}
		return concepts.get(0).getAncestors();
	}

	public Set<Long> retrieveDescendants(String conceptId, QueryBuilder branchCriteria, boolean stated) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termQuery("ancestors", conceptId))
						.must(termQuery("stated", stated))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryIndexConcept> concepts = elasticsearchTemplate.queryForPage(searchQuery, QueryIndexConcept.class).getContent();
		return concepts.stream().map(QueryIndexConcept::getConceptId).collect(Collectors.toSet());
	}

	public void updateStatedAndInferredTransitiveClosures(Commit commit) {
		updateTransitiveClosure(commit, true);
		updateTransitiveClosure(commit, false);
	}

	public void updateTransitiveClosure(Commit commit, boolean stated) {
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

		logger.info("Performing incremental update of {} transitive closures", formName);
		final String branchPath = commit.getBranch().getFatPath();

		// Step: Collect source and destination of changed is-a relationships
		Set<Long> relevantConceptIds = new HashSet<>();
		try (final CloseableIterator<Relationship> changedInferredIsARelationships = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteriaOpenCommitChangesOnly(commit))
						.must(termQuery("typeId", Concepts.ISA))
						.must(termsQuery("characteristicTypeId", characteristicTypeIds))
				)
				.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
			changedInferredIsARelationships.forEachRemaining(relationship -> {
				relevantConceptIds.add(parseLong(relationship.getSourceId()));
				relevantConceptIds.add(parseLong(relationship.getDestinationId()));
			});
		}

		if (relevantConceptIds.isEmpty()) {
			logger.info("No {} changes found. Nothing to do.", formName);
			return;
		}

		// Step: Identify existing nodes which will be touched
		// Strategy: Load existing nodes where id or TC matches updated relationship source or destination ids
		Set<Long> existingNodes = new HashSet<>();
		try (final CloseableIterator<QueryIndexConcept> existingIndexConcepts = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteria(branchPath))
				)
				.withFilter(boolQuery()
						.should(termsQuery("conceptId", relevantConceptIds))
						.should(termsQuery("ancestors", relevantConceptIds)))
				.withPageable(ConceptService.LARGE_PAGE).build(), QueryIndexConcept.class)) {
			existingIndexConcepts.forEachRemaining(indexConcept -> {
				existingNodes.add(indexConcept.getConceptId());
				existingNodes.addAll(indexConcept.getAncestors());
			});
		}


		// Step: Build existing graph
		// Strategy: Load relationships of matched nodes and build existing graph(s)
		final GraphBuilder graphBuilder = new GraphBuilder();
		try (final CloseableIterator<Relationship> existingInferredIsARelationships = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteria(branchPath))
						.must(termQuery("active", true))
						.must(termQuery("typeId", Concepts.ISA))
						.must(termsQuery("characteristicTypeId", characteristicTypeIds))
				)
				.withFilter(boolQuery().must(termsQuery("sourceId", existingNodes)))
				.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
			existingInferredIsARelationships.forEachRemaining(relationship ->
					graphBuilder.addParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()))
			);
		}
		logger.info("{} existing nodes loaded.", graphBuilder.getNodeCount());


		// Step - Update graph
		// Strategy: Add/remove edges from new commit
		AtomicLong relationshipsAdded = new AtomicLong();
		AtomicLong relationshipsRemoved = new AtomicLong();
		try (final CloseableIterator<Relationship> existingInferredIsARelationships = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteriaOpenCommitChangesOnly(commit))
						.must(termQuery("typeId", Concepts.ISA))
						.must(termsQuery("characteristicTypeId", characteristicTypeIds))
				)
				.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
			existingInferredIsARelationships.forEachRemaining(relationship -> {
				if (relationship.isActive()) {
					graphBuilder.addParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()));
					relationshipsAdded.incrementAndGet();
				} else {
					graphBuilder.removeParent(parseLong(relationship.getSourceId()), parseLong(relationship.getDestinationId()));
					relationshipsRemoved.incrementAndGet();
				}
			});
		}
		logger.info("{} {} is-a relationships added, {} removed.", relationshipsAdded.get(), formName, relationshipsRemoved.get());


		// Step: Save changes
		Set<QueryIndexConcept> indexConceptsToSave = new HashSet<>();
		graphBuilder.getNodes().forEach(node -> {
			final Set<Long> transitiveClosure = node.getTransitiveClosure();
			final Long nodeId = node.getId();
			indexConceptsToSave.add(new QueryIndexConcept(nodeId, transitiveClosure, stated));
		});
		if (!indexConceptsToSave.isEmpty()) {
			doSaveBatch(indexConceptsToSave, commit);
		}
		logger.info("{} {} concept transitive closures updated.", indexConceptsToSave.size(), formName);
	}

	public void doSaveBatch(Collection<QueryIndexConcept> indexConcepts, Commit commit) {
		doSaveBatchComponents(indexConcepts, commit, "conceptId", queryIndexConceptRepository);
	}

	public void deleteAll() {
		queryIndexConceptRepository.deleteAll();
	}
}
