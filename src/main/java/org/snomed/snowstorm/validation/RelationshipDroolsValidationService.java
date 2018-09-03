package org.snomed.snowstorm.validation;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class RelationshipDroolsValidationService implements org.ihtsdo.drools.service.RelationshipService {

	private ElasticsearchOperations elasticsearchTemplate;
	private BranchCriteria branchCriteria;

	RelationshipDroolsValidationService(BranchCriteria branchCriteria, ElasticsearchOperations elasticsearchTemplate) {
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
				.must(branchCriteria.getEntityBranchCriteria(Relationship.class))
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
