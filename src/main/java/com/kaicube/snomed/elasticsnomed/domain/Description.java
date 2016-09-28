package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.rest.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import static com.kaicube.snomed.elasticsnomed.domain.Concepts.*;

@Document(type = "description", indexName = "snomed")
public class Description extends SnomedComponent<Description> {

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
	@NotNull
	@Size(min = 5, max = 18)
	private String moduleId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 2, max = 2)
	private String languageCode;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String typeId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String caseSignificanceId;

	// Populated when requesting an update
	private Map<String, String> acceptabilityMap;

	@JsonIgnore
	// Populated manually when loading from store
	private Map<String, LanguageReferenceSetMember> langRefsetMembers;

	private static final Logger logger = LoggerFactory.getLogger(Description.class);

	public Description() {
		term = "";
		moduleId = "";
		languageCode = "";
		typeId = "";
		caseSignificanceId = "";
		acceptabilityMap = new HashMap<>();
		langRefsetMembers = new HashMap<>();
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
		setEffectiveTime(effectiveTime);
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

	@Override
	protected Object[] getReleaseHashObjects() {
		return new Object[] {active, term, moduleId, languageCode, typeId, caseSignificanceId};
	}

	@JsonView(value = View.Component.class)
	public String getType() {
		return descriptionTypeNames.get(typeId);
	}

	public void setType(String type) {
		typeId = descriptionTypeNames.inverse().get(type);
	}

	@JsonView(value = View.Component.class)
	public String getCaseSignificance() {
		return caseSignificanceNames.get(caseSignificanceId);
	}

	public void setCaseSignificance(String caseSignificance) {
		caseSignificanceId = caseSignificanceNames.inverse().get(caseSignificance);
	}

	@JsonView(value = View.Component.class)
	public String getLang() {
		return languageCode;
	}

	public void setLang(String languageCode) {
		this.languageCode = languageCode;
	}

	public Description addLanguageRefsetMember(LanguageReferenceSetMember member) {
		member.setReferencedComponentId(descriptionId);
		final LanguageReferenceSetMember previousMember = langRefsetMembers.put(member.getRefsetId(), member);
		if (previousMember != null) {
			logger.debug("Lang member replaced other:\n{}\n{}", member, previousMember);
		}
		return this;
	}

	@Override
	@JsonIgnore
	public String getId() {
		return descriptionId;
	}

	public Map<String, String> getAcceptabilityMapFromLangRefsetMembers() {
		Map<String, String> map = new HashMap<>();
		for (LanguageReferenceSetMember member : langRefsetMembers.values()) {
			if (member.isActive()) map.put(member.getRefsetId(), descriptionAcceptabilityNames.get(member.getAcceptabilityId()));
		}
		return map;
	}

	public Map<String, LanguageReferenceSetMember> getLangRefsetMembers() {
		return langRefsetMembers;
	}

	@JsonView(value = View.Component.class)
	public Map<String, String> getAcceptabilityMap() {
		if (!langRefsetMembers.isEmpty()) {
			return getAcceptabilityMapFromLangRefsetMembers();
		} else {
			return acceptabilityMap;
		}
	}

	public void setAcceptabilityMap(Map<String, String> acceptabilityMap) {
		this.acceptabilityMap = acceptabilityMap;
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

		return descriptionId != null ? descriptionId.equals(that.descriptionId) : that.descriptionId == null;

	}

	@Override
	public int hashCode() {
		return descriptionId != null ? descriptionId.hashCode() : 0;
	}

	@Override
	public String toString() {
		return "Description{" +
				"descriptionId='" + descriptionId + '\'' +
				", active=" + active +
				", term='" + term + '\'' +
				", conceptId='" + conceptId + '\'' +
				", effectiveTime='" + getEffectiveTime() + '\'' +
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
