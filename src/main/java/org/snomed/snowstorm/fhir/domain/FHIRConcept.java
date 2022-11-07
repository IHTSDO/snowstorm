package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.entity.TermConceptDesignation;
import ca.uhn.fhir.jpa.entity.TermConceptParentChildLink;
import ca.uhn.fhir.jpa.entity.TermConceptProperty;
import org.hl7.fhir.r4.model.CodeSystem;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.*;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

@Document(indexName = "fhir-concept")
public class FHIRConcept implements FHIRGraphNode {

	public interface Fields {

		String CODE_SYSTEM_VERSION = "codeSystemVersion";
		String CODE = "code";
		String DISPLAY = "display";
		String DISPLAY_LENGTH = "displayLen";
		String PARENTS = "parents";
		String ANCESTORS = "ancestors";
	}
	@Id
	// Internal ID
	private String id;

	@Field(type = FieldType.Keyword)
	private String codeSystemVersion;

	@Field(type = FieldType.Keyword)
	private String code;

	private String display;

	@Field(type = FieldType.Integer)
	private Integer displayLen;

	private final boolean active;

	@Field(type = FieldType.Keyword)
	private Set<String> parents;

	@Field(type = FieldType.Keyword)
	private Set<String> ancestors;

	private List<FHIRDesignation> designations;

	private Map<String, List<FHIRProperty>> properties;

	public FHIRConcept() {
		active = true;
	}

	public FHIRConcept(TermConcept termConcept, FHIRCodeSystemVersion codeSystemVersion) {
		this.codeSystemVersion = codeSystemVersion.getId();

		code = termConcept.getCode();
		setDisplay(termConcept.getDisplay());
		active = true;

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
		// Ancestors will be set before save
	}


	public FHIRConcept(CodeSystem.ConceptDefinitionComponent definitionConcept, FHIRCodeSystemVersion codeSystemVersion) {
		this.codeSystemVersion = codeSystemVersion.getId();

		code = definitionConcept.getCode();
		setDisplay(definitionConcept.getDisplay());

		active = true;

		designations = definitionConcept.getDesignation().stream()
				.map(FHIRDesignation::new)
				.collect(Collectors.toList());

		properties = new HashMap<>();
		parents = new HashSet<>();
		for (CodeSystem.ConceptPropertyComponent propertyComponent : definitionConcept.getProperty()) {
			properties.computeIfAbsent(propertyComponent.getCode(), k -> new ArrayList<>()).add(new FHIRProperty(propertyComponent));
			if (propertyComponent.getCode().equals("parent") || propertyComponent.getCode().equals("subsumedBy")) {
				parents.add(propertyComponent.hasValueCoding() ? propertyComponent.getValueCoding().getCode() : propertyComponent.getValue().toString());
			}
		}
		// Ancestors will be set before save
	}


	public FHIRConcept(ConceptMini snomedConceptMini, FHIRCodeSystemVersion codeSystemVersion, boolean includeDesignations) {
		this.codeSystemVersion = codeSystemVersion.getId();
		code = snomedConceptMini.getConceptId();
		TermLangPojo displayTerm = snomedConceptMini.getPt();
		if (displayTerm == null) {
			displayTerm = snomedConceptMini.getFsn();
			if (displayTerm == null) {
				displayTerm = new TermLangPojo(code, "en");
			}
		}
		setDisplay(displayTerm.getTerm());
		active = !Boolean.FALSE.equals(snomedConceptMini.getActive());
		if (includeDesignations) {
			designations = new ArrayList<>();
			// Add display
			designations.add(new FHIRDesignation(displayTerm.getLang(), FHIRConstants.HL7_DESIGNATION_USAGE, FHIRConstants.DISPLAY, displayTerm.getTerm()));

			// Add other descriptions with acceptability, and then any others without 'use'.
			List<Description> activeDescriptions = new ArrayList<>(snomedConceptMini.getActiveDescriptions());
			List<LanguageDialect> requestedLanguageDialects = snomedConceptMini.getRequestedLanguageDialects();
			activeDescriptions.sort(Comparator.comparing(Description::getType).thenComparing(description -> !description.hasAcceptability(requestedLanguageDialects)));
			for (Description description : activeDescriptions) {
				FHIRDesignation designation = new FHIRDesignation(description.getLanguageCode(), description.getTerm());
				if (description.hasAcceptability(requestedLanguageDialects)) {
					designation.setUse(SNOMED_URI, description.getTypeId());
				}
				designations.add(designation);
			}
		}
	}

	@Override
	public String getCodeField() {
		return "code";
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
		this.displayLen = display != null ? display.length() : 0;
	}

	public Integer getDisplayLen() {
		return displayLen;
	}

	public boolean isActive() {
		return active;
	}

	public Set<String> getParents() {
		return parents;
	}

	public void setParents(Set<String> parents) {
		this.parents = parents;
	}

	public Set<String> getAncestors() {
		return ancestors;
	}

	public void setAncestors(Set<String> ancestors) {
		this.ancestors = ancestors;
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
