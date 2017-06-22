package org.ihtsdo.elasticsnomed.core.data.domain;

import io.kaicode.elasticvc.domain.DomainEntity;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Set;

@Document(type = "query-concept", indexName = "snomed-index", shards = 8)
public class QueryConceptId extends DomainEntity<QueryConceptId> {

	@Field(type = FieldType.Long, index = FieldIndex.not_analyzed)
	private Long conceptId;

	public QueryConceptId() {
	}

	public Long getConceptId() {
		return conceptId;
	}

	@Override
	public String getId() {
		return conceptId.toString();
	}

	@Override
	public boolean isComponentChanged(QueryConceptId existingComponent) {
		return false;
	}
}
