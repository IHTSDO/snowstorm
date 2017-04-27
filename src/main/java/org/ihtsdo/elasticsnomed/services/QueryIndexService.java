package org.ihtsdo.elasticsnomed.services;

import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.elasticsearch.index.query.QueryBuilders;
import org.ihtsdo.elasticsnomed.domain.Concepts;
import org.ihtsdo.elasticsnomed.domain.QueryIndexConcept;
import org.ihtsdo.elasticsnomed.domain.Relationship;
import org.ihtsdo.elasticsnomed.repositories.QueryIndexConceptRepository;
import org.ihtsdo.elasticsnomed.services.transitiveclosure.GraphBuilder;
import org.ihtsdo.elasticsnomed.services.transitiveclosure.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

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

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Set<Long> retrieveAncestors(String conceptId, String path) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteria(path))
						.must(termQuery("conceptId", conceptId))
				)
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

	public void updateStatedTransitiveClosure(Commit commit) {
		logger.info("Performing incremental update of stated transitive closures");
		final String branchPath = commit.getBranch().getFatPath();

		// Step: Collect source and destination of changed is-a relationships
		Set<Long> relevantConceptIds = new HashSet<>();
		try (final CloseableIterator<Relationship> changedInferredIsARelationships = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteriaOpenCommitChangesOnly(commit))
						.must(termQuery("typeId", Concepts.ISA))
						.must(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP))
				)
				.withPageable(ConceptService.LARGE_PAGE).build(), Relationship.class)) {
			changedInferredIsARelationships.forEachRemaining(relationship -> {
				relevantConceptIds.add(parseLong(relationship.getSourceId()));
				relevantConceptIds.add(parseLong(relationship.getDestinationId()));
			});
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
						.must(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP))
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
						.must(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP))
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
		logger.info("{} is-a relationships added, {} removed.", relationshipsAdded.get(), relationshipsRemoved.get());


		// Step: Save changes
		// Strategy: Persist all nodes excluding root nodes but including the Snomed CT root
		Set<QueryIndexConcept> indexConceptsToSave = new HashSet<>();
		graphBuilder.getNodes().forEach(node -> {
			final Set<Long> transitiveClosure = node.getTransitiveClosure();
			final Long nodeId = node.getId();
			if (!transitiveClosure.isEmpty() || nodeId.toString().equals(Concepts.SNOMEDCT_ROOT)) {
				indexConceptsToSave.add(new QueryIndexConcept(nodeId, transitiveClosure));
			}
		});
		if (!indexConceptsToSave.isEmpty()) {
			doSaveBatch(indexConceptsToSave, commit);
		}
		logger.info("{} concept transitive closures updated.", indexConceptsToSave.size());
	}

	public void doSaveBatch(Collection<QueryIndexConcept> indexConcepts, Commit commit) {
		doSaveBatchComponents(indexConcepts, commit, "conceptId", queryIndexConceptRepository);
	}

	public void deleteAll() {
		queryIndexConceptRepository.deleteAll();
	}
}
