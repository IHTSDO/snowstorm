package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.entity.TermConceptDesignation;
import ca.uhn.fhir.jpa.entity.TermConceptParentChildLink;
import ca.uhn.fhir.jpa.entity.TermConceptProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.*;

@Document(indexName = "fhir-concept")
public class FHIRConcept {

	@Id
	// Internal ID
	private String id;

	@Field(type = FieldType.Keyword)
	private String codeSystemVersion;

	@Field(type = FieldType.Keyword)
	private String code;

	private String display;

	@Field(type = FieldType.Keyword)
	private Set<String> parents;

	private List<FHIRDesignation> designations;

	private Map<String, List<FHIRProperty>> properties;

	public FHIRConcept() {
	}

	public FHIRConcept(TermConcept termConcept, FHIRCodeSystemVersion codeSystemVersion) {
		this.codeSystemVersion = codeSystemVersion.getIdAndVersion();

		code = termConcept.getCode();
		display = termConcept.getDisplay();

		designations = new ArrayList<>();
		for (TermConceptDesignation designation : termConcept.getDesignations()) {
			designations.add(new FHIRDesignation(designation));
		}

		properties = new HashMap<>();
		for (TermConceptProperty property : termConcept.getProperties()) {
			properties.computeIfAbsent(property.getKey(), (i) -> new ArrayList<>())
					.add(new FHIRProperty(property));
		}

		parents = new HashSet<>();
		for (TermConceptParentChildLink parent : termConcept.getParents()) {
			parents.add(parent.getParent().getCode());
		}
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCodeSystemVersion() {
		return codeSystemVersion;
	}

	public void setCodeSystemVersion(String codeSystemVersion) {
		this.codeSystemVersion = codeSystemVersion;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDisplay() {
		return display;
	}

	public void setDisplay(String display) {
		this.display = display;
	}

	public Set<String> getParents() {
		return parents;
	}

	public void setParents(Set<String> parents) {
		this.parents = parents;
	}

	public List<FHIRDesignation> getDesignations() {
		return designations;
	}

	public void setDesignations(List<FHIRDesignation> designations) {
		this.designations = designations;
	}

	public Map<String, List<FHIRProperty>> getProperties() {
		return properties;
	}

	public void setProperties(Map<String, List<FHIRProperty>> properties) {
		this.properties = properties;
	}
}
