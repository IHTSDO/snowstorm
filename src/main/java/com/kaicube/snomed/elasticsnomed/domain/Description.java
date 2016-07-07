package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.HashMap;
import java.util.Map;

@Document(type = "description", indexName = "snomed")
public class Description extends Entity {

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String descriptionId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private boolean active;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.analyzed)
	private String term;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String conceptId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String effectiveTime;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String moduleId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String languageCode;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String typeId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String caseSignificanceId;

	@JsonIgnore
	private Map<String, String> acceptabilityMap;

	public Description() {
		acceptabilityMap = new HashMap<>();
	}

	public Description(String term) {
		this();
		this.term = term;
	}

	public Description(String id, String effectiveTime, boolean active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		this();
		this.descriptionId = id;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.moduleId = moduleId;
		this.conceptId = conceptId;
		this.languageCode = languageCode;
		this.typeId = typeId;
		this.term = term;
		this.caseSignificanceId = caseSignificanceId;
	}

	public void addAcceptability(String languageReferenceSetId, String acceptabilityId) {
		acceptabilityMap.put(languageReferenceSetId, acceptabilityId);
	}

	@JsonView(value = View.Component.class)
	public Map<String, String> getAcceptabilityMap() {
		return acceptabilityMap;
	}

	public String getDescriptionId() {
		return descriptionId;
	}

	public void setDescriptionId(String descriptionId) {
		this.descriptionId = descriptionId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

	public String getTypeId() {
		return typeId;
	}

	public void setTypeId(String typeId) {
		this.typeId = typeId;
	}

	public String getCaseSignificanceId() {
		return caseSignificanceId;
	}

	public void setCaseSignificanceId(String caseSignificanceId) {
		this.caseSignificanceId = caseSignificanceId;
	}

	@Override
	public String toString() {
		return "Description{" +
				"descriptionId='" + descriptionId + '\'' +
				", active=" + active +
				", term='" + term + '\'' +
				", conceptId='" + conceptId + '\'' +
				", effectiveTime='" + effectiveTime + '\'' +
				", moduleId='" + moduleId + '\'' +
				", languageCode='" + languageCode + '\'' +
				", typeId='" + typeId + '\'' +
				", caseSignificanceId='" + caseSignificanceId + '\'' +
				", internalId='" + getInternalId() + '\'' +
				", commit='" + getCommit() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
