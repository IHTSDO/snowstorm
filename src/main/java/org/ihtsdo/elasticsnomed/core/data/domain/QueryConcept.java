package org.ihtsdo.elasticsnomed.core.data.domain;

import io.kaicode.elasticvc.domain.DomainEntity;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Set;

/**
 * Represents an active concept with fields to assist logical searching.
 */
@Document(type = "query-concept", indexName = "snomed-index", shards = 8)
public class QueryConcept extends DomainEntity<QueryConcept> {

	public static final String CONCEPT_ID_FIELD = "conceptId";
	public static final String PARENTS_FIELD = "parents";
	public static final String ANCESTORS_FIELD = "ancestors";
	public static final String STATED_FIELD = "stated";

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String conceptIdForm;

	@Field(type = FieldType.Long, index = FieldIndex.not_analyzed)
	private Long conceptId;

	@Field(type = FieldType.Long, index = FieldIndex.not_analyzed)
	private Set<Long> parents;

	@Field(type = FieldType.Long, index = FieldIndex.not_analyzed)
	private Set<Long> ancestors;

	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private boolean stated;

	public QueryConcept() {
	}

	public QueryConcept(Long conceptId, Set<Long> parentIds, Set<Long> ancestorIds, boolean stated) {
		this.conceptId = conceptId;
		this.parents = parentIds;
		this.ancestors = ancestorIds;
		this.stated = stated;
		updateConceptIdForm();
	}

	private void updateConceptIdForm() {
		this.conceptIdForm = conceptId + (stated ? "_s" : "_i");
	}

	@Override
	public boolean isChanged() {
		return true;
	}

	@Override
	public String getId() {
		return conceptIdForm;
	}

	@Override
	public boolean isComponentChanged(QueryConcept that) {
		return that == null
				|| !ancestors.equals(that.ancestors);
	}

	public Long getConceptId() {
		return conceptId;
	}

	public Set<Long> getParents() {
		return parents;
	}

	public Set<Long> getAncestors() {
		return ancestors;
	}

	public boolean isStated() {
		return stated;
	}

	public void setConceptIdForm(String conceptIdForm) {
		this.conceptIdForm = conceptIdForm;
	}

	public String getConceptIdForm() {
		return conceptIdForm;
	}

	public void setConceptId(Long conceptId) {
		this.conceptId = conceptId;
	}

	public void setParents(Set<Long> parents) {
		this.parents = parents;
	}

	public void setAncestors(Set<Long> ancestors) {
		this.ancestors = ancestors;
	}

	public void setStated(boolean stated) {
		this.stated = stated;
	}

	@Override
	public String toString() {
		return "QueryConcept{" +
				"conceptIdForm=" + conceptIdForm +
				", conceptId=" + conceptId +
				", parents=" + parents +
				", ancestors=" + ancestors +
				", stated=" + stated +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
