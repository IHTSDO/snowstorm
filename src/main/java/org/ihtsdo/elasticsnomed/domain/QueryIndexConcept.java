package org.ihtsdo.elasticsnomed.domain;

import io.kaicode.elasticvc.domain.DomainEntity;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Set;

@Document(type = "concept-index", indexName = "snomed-index", shards = 8)
public class QueryIndexConcept extends DomainEntity<QueryIndexConcept> {

	@Field(type = FieldType.Long, index = FieldIndex.not_analyzed)
	private Long conceptId;

	@Field(type = FieldType.Long, index = FieldIndex.not_analyzed)
	private Set<Long> ancestors;

	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private boolean stated;

	public QueryIndexConcept() {
	}

	public QueryIndexConcept(Long conceptId, Set<Long> ancestorIds, boolean stated) {
		this.conceptId = conceptId;
		this.ancestors = ancestorIds;
		this.stated = stated;
	}

	@Override
	public boolean isChanged() {
		return true;
	}

	@Override
	public String getId() {
		return conceptId.toString();
	}

	@Override
	public boolean isComponentChanged(QueryIndexConcept that) {
		return that == null
				|| !ancestors.equals(that.ancestors);
	}

	public Long getConceptId() {
		return conceptId;
	}

	public void setConceptId(Long conceptId) {
		this.conceptId = conceptId;
	}

	public Set<Long> getAncestors() {
		return ancestors;
	}

	public void setAncestors(Set<Long> ancestors) {
		this.ancestors = ancestors;
	}

	public boolean isStated() {
		return stated;
	}

	public void setStated(boolean stated) {
		this.stated = stated;
	}

	@Override
	public String toString() {
		return "QueryIndexConcept{" +
				"conceptId=" + conceptId +
				", ancestors=" + ancestors +
				", stated=" + stated +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
