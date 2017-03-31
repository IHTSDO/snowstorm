package org.ihtsdo.elasticsnomed.domain;

import io.kaicode.elasticvc.domain.DomainEntity;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Set;

@Document(type = "concept", indexName = "snomed-index", shards = 8)
public class QueryIndexConcept extends DomainEntity<QueryIndexConcept> {

	private Long conceptId;
	private Set<Long> ancestors;

	public QueryIndexConcept() {
	}

	public QueryIndexConcept(Long conceptId, Set<Long> ancestorIds) {
		this.conceptId = conceptId;
		this.ancestors = ancestorIds;
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

	@Override
	public String toString() {
		return "QueryIndexConcept{" +
				"conceptId=" + conceptId +
				", ancestors=" + ancestors +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
