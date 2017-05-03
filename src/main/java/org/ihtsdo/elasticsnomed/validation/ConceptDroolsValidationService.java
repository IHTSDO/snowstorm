package org.ihtsdo.elasticsnomed.validation;

import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.drools.exception.RuleExecutorException;
import org.ihtsdo.elasticsnomed.Config;
import org.ihtsdo.elasticsnomed.domain.Concept;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class ConceptDroolsValidationService implements org.ihtsdo.drools.service.ConceptService {

	private final String branchPath;
	private final QueryBuilder branchCriteria;
	private final ElasticsearchOperations elasticsearchTemplate;

	ConceptDroolsValidationService(String branchPath, QueryBuilder branchCriteria, ElasticsearchOperations elasticsearchTemplate) {
		this.branchPath = branchPath;
		this.branchCriteria = branchCriteria;
		this.elasticsearchTemplate = elasticsearchTemplate;
	}

	@Override
	public boolean isActive(String conceptId) {
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termQuery("conceptId", conceptId)))
				.withPageable(Config.PAGE_OF_ONE)
				.build();
		List<Concept> matches = elasticsearchTemplate.queryForList(query, Concept.class);
		if (matches.isEmpty()) {
			throw new RuleExecutorException(String.format("Concept '%s' not found on branch '%s'", conceptId, branchPath));
		}
		return matches.get(0).isActive();
	}
}
