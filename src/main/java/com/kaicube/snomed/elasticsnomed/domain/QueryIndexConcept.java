package com.kaicube.snomed.elasticsnomed.domain;

import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Set;

@Document(type = "concept", indexName = "snomed-index")
public class QueryIndexConcept extends Entity {

	private Long conceptId;
	private Set<Long> ancestors;

	public QueryIndexConcept() {
	}

	public QueryIndexConcept(Long conceptId, Set<Long> ancestorIds) {
		this.conceptId = conceptId;
		this.ancestors = ancestorIds;
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
