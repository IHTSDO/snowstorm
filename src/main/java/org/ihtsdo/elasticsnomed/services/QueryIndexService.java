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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class QueryIndexService extends ComponentService {

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
		return concepts.isEmpty() ? null : concepts.get(0).getAncestors();
	}

	// TODO: This will be used when saving classification results.
	public void performIncrementalTransitiveClosureUpdate() {
		// Step: Grab existing nodes which will be updated
		// Strategy: Load existing nodes where id or TC matches updated relationship source ids

		// Step: Build existing graph
		// Strategy: Load relationships of matched nodes and build existing graph(s)

		// Step - Update graph
		// Strategy: Add/remove edges from new commit

		// Step: Save changes
		// Strategy: Persist all nodes excluding root nodes but including the Snomed CT root
	}

	public void createTransitiveClosureForEveryConcept(Commit commit) {
		logger.info("Calculating transitive closures");
		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteriaWithinOpenCommit(commit))
						.must(QueryBuilders.termQuery("typeId", Concepts.ISA))
						.must(termQuery("characteristicTypeId", Concepts.INFERRED_RELATIONSHIP))
				)
				.withPageable(LARGE_PAGE);

		final GraphBuilder graphBuilder = new GraphBuilder();

		final AtomicLong activeRelationshipCount = new AtomicLong(0);
		try (final CloseableIterator<Relationship> relationshipStream = elasticsearchTemplate.stream(queryBuilder.build(), Relationship.class)) {
			relationshipStream.forEachRemaining(relationship -> {
				if (relationship.isActive()) {
					graphBuilder.addEdge(Long.parseLong(relationship.getSourceId()), Long.parseLong(relationship.getDestinationId()));
					activeRelationshipCount.incrementAndGet();
				}
			});
		}

		logger.info("Processed {} active relationships.", activeRelationshipCount.get());

		final List<QueryIndexConcept> conceptsToSave = new ArrayList<>();
		int i = 0;
		for (Node node : graphBuilder.getNodes()) {
			final QueryIndexConcept indexConcept = new QueryIndexConcept(node.getId(), node.getTransitiveClosure());
			indexConcept.setChanged(true);
			conceptsToSave.add(indexConcept);
			i++;
			if (i % 100000 == 0) {
				doSaveBatch(conceptsToSave, commit);
				conceptsToSave.clear();
			}
		}
		doSaveBatch(conceptsToSave, commit);
	}

	public void doSaveBatch(Collection<QueryIndexConcept> indexConcepts, Commit commit) {
		doSaveBatchComponents(indexConcepts, commit, "conceptId", queryIndexConceptRepository);
	}

	public void deleteAll() {
		queryIndexConceptRepository.deleteAll();
	}
}
