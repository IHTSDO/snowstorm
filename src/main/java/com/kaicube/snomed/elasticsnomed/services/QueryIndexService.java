package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.elasticversioncontrol.api.ComponentService;
import com.kaicube.elasticversioncontrol.api.VersionControlHelper;
import com.kaicube.elasticversioncontrol.domain.Commit;
import com.kaicube.snomed.elasticsnomed.domain.Concepts;
import com.kaicube.snomed.elasticsnomed.domain.QueryIndexConcept;
import com.kaicube.snomed.elasticsnomed.domain.Relationship;
import com.kaicube.snomed.elasticsnomed.repositories.QueryIndexConceptRepository;
import com.kaicube.snomed.elasticsnomed.services.transitiveclosure.GraphBuilder;
import com.kaicube.snomed.elasticsnomed.services.transitiveclosure.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
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
	private ElasticsearchTemplate elasticsearchTemplate;

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

	public void createTransitiveClosureForEveryConcept(Commit commit) {
		logger.info("Calculating transitive closures");
		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteriaWithinOpenCommit(commit))
						.must(termQuery("typeId", Concepts.ISA))
						.must(termQuery("characteristicTypeId", Concepts.INFERRED_RELATIONSHIP))
				)
				.withPageable(ConceptService.LARGE_PAGE);

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
