package org.ihtsdo.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.elasticsnomed.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Document(type = "member", indexName = "snomed")
public class ReferenceSetMember<C extends ReferenceSetMember> extends SnomedComponent<C> {

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)// TODO Consider not indexing this (FieldIndex.no)
	private String memberId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private boolean active;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String moduleId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String refsetId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String referencedComponentId;

	// Used when the referencedComponentId is a description (or possibly a relationship later)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String conceptId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Object)
	private Map<String, String> additionalFields;

	public ReferenceSetMember() {
		additionalFields = new HashMap<>();
	}

	public ReferenceSetMember(String memberId, String effectiveTime, boolean active, String moduleId, String refsetId,
			String referencedComponentId) {
		this();
		this.memberId = memberId;
		setEffectiveTime(effectiveTime);
		this.active = active;
		this.moduleId = moduleId;
		this.refsetId = refsetId;
		this.referencedComponentId = referencedComponentId;
	}

	public ReferenceSetMember(String refsetId, String referencedComponentId) {
		this(UUID.randomUUID().toString(), null, true, Concepts.CORE_MODULE, refsetId, referencedComponentId);
	}

	@Override
	public boolean isComponentChanged(C that) {
		return that == null
				|| active != that.isActive()
				|| !moduleId.equals(that.getModuleId())
				|| !additionalFields.equals(that.getAdditionalFields());
	}

	@Override
	protected Object[] getReleaseHashObjects() {
		return new Object[] {active, moduleId};
	}

	public String getAdditionalField(String fieldName) {
		return getAdditionalFields().get(fieldName);
	}

	public void setAdditionalField(String fieldName, String value) {
		getAdditionalFields().put(fieldName, value);
	}


	@Override
	@JsonIgnore
	public String getId() {
		return getMemberId();
	}

	public String getMemberId() {
		return memberId;
	}

	public void setMemberId(String memberId) {
		this.memberId = memberId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getRefsetId() {
		return refsetId;
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	public void setReferencedComponentId(String referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public Map<String, String> getAdditionalFields() {
		return additionalFields;
	}

	public void setAdditionalFields(Map<String, String> additionalFields) {
		this.additionalFields = additionalFields;
	}

	@Override
	public String toString() {
		return "ReferenceSetMember{" +
				"memberId='" + memberId + '\'' +
				", effectiveTime='" + getEffectiveTime() + '\'' +
				", active=" + active +
				", moduleId='" + moduleId + '\'' +
				", refsetId='" + refsetId + '\'' +
				", referencedComponentId='" + referencedComponentId + '\'' +
				", additionalFields='" + additionalFields + '\'' +
				", conceptId='" + conceptId + '\'' +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}

}
