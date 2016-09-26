package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.elasticversioncontrol.domain.Component;
import com.kaicube.snomed.elasticsnomed.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Document(type = "description", indexName = "snomed")
public class Description extends Component<Description> {

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@Size(min = 5, max = 18)
	private String descriptionId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private boolean active;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.analyzed)
	@NotNull
	@Size(min = 1, max = 255)
	private String term;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String conceptId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String effectiveTime;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String moduleId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 2, max = 2)
	private String languageCode;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String typeId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String caseSignificanceId;

	@JsonIgnore
	// Populated when requesting an update
	private Map<String, String> acceptabilityMap;

	@JsonIgnore
	// Populated when loading from store
	private Set<LanguageReferenceSetMember> langRefsetMembers;

	public Description() {
		term = "";
		moduleId = "";
		languageCode = "";
		typeId = "";
		caseSignificanceId = "";
		acceptabilityMap = new HashMap<>();
		langRefsetMembers = new HashSet<>();
	}

	public Description(String term) {
		this();
		this.descriptionId = term.hashCode() + "";
		this.term = term;
	}

	public Description(String id, String term) {
		this(term);
		this.descriptionId = id;
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

	@Override
	public boolean isComponentChanged(Description that) {
		return that == null
				|| active != that.active
				|| !term.equals(that.term)
				|| !moduleId.equals(that.moduleId)
				|| !languageCode.equals(that.languageCode)
				|| !typeId.equals(that.typeId)
				|| !caseSignificanceId.equals(that.caseSignificanceId);
	}

	public Description addAcceptability(String languageReferenceSetId, String acceptabilityId) {
		acceptabilityMap.put(languageReferenceSetId, acceptabilityId);
		return this;
	}

	public Description addLanguageRefsetMember(LanguageReferenceSetMember member) {
		langRefsetMembers.add(member);
		return this;
	}

	@Override
	@JsonIgnore
	public String getId() {
		return descriptionId;
	}

	@JsonView(value = View.Component.class)
	@JsonProperty(value = "acceptabilityMap")
	public Map<String, String> getAcceptabilityMapFromLangRefsetMembers() {
		Map<String, String> map = new HashMap<>();
		for (LanguageReferenceSetMember member : langRefsetMembers) {
			if (member.isActive()) map.put(member.getRefsetId(), member.getAcceptabilityId());
		}
		return map;
	}

	public Set<LanguageReferenceSetMember> getLangRefsetMembers() {
		return langRefsetMembers;
	}

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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Description that = (Description) o;

		return descriptionId.equals(that.descriptionId);

	}

	@Override
	public int hashCode() {
		return descriptionId.hashCode();
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
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
