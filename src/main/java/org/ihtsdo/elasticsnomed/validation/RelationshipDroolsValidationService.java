package org.ihtsdo.elasticsnomed.validation;

import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.Config;
import org.ihtsdo.elasticsnomed.domain.Relationship;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class RelationshipDroolsValidationService implements org.ihtsdo.drools.service.RelationshipService {

	private ElasticsearchOperations elasticsearchTemplate;
	private QueryBuilder branchCriteria;

	RelationshipDroolsValidationService(QueryBuilder branchCriteria, ElasticsearchOperations elasticsearchTemplate) {
		this.branchCriteria = branchCriteria;
		this.elasticsearchTemplate = elasticsearchTemplate;
	}

	@Override
	public boolean hasActiveInboundStatedRelationship(String conceptId) {
		return hasActiveInboundStatedRelationship(conceptId, null);
	}

	@Override
	public boolean hasActiveInboundStatedRelationship(String conceptId, String relationshipTypeId) {
		final BoolQueryBuilder builder = boolQuery()
				.must(branchCriteria)
				.must(termQuery("sourceId", conceptId))
				.must(termQuery("active", true));
		if (!Strings.isNullOrEmpty(relationshipTypeId)) {
			builder.must(termQuery("typeId", relationshipTypeId));
		}
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(builder)
				.withPageable(Config.PAGE_OF_ONE)
				.build();
		return !elasticsearchTemplate.queryForList(query, Relationship.class).isEmpty();
	}
}
