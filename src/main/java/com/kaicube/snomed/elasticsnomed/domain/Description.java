package com.kaicube.snomed.elasticsnomed.domain;


import org.springframework.data.elasticsearch.annotations.Document;

@Document(type = "description", indexName = "snomed")
public class Description extends Component {

	private String descriptionId;
	private boolean active;
	private String term;
	private String conceptId;
	private String effectiveTime;
	private String moduleId;
	private String languageCode;
	private String typeId;
	private String caseSignificanceId;

	public Description() {
	}

	public Description(String id, String effectiveTime, boolean active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
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
				", uuid='" + getUuid() + '\'' +
				", commit='" + getCommit() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}

}
