package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.jpa.entity.TermConceptDesignation;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ValueSet;
import org.snomed.snowstorm.core.data.domain.Description;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

public class FHIRDesignation {

	private String language;
	private String use;
	private String value;



	private List<FHIRExtension> extensions;

	public FHIRDesignation() {
	}

	public FHIRDesignation(TermConceptDesignation designation) {
		language = designation.getLanguage();
		value = designation.getValue();
		setUse(designation.getUseSystem(), designation.getUseCode());
	}

	public FHIRDesignation(Description description) {
		language = description.getLanguageCode();
		use = SNOMED_URI + "|" + description.getAcceptabilityMap();
		value = description.getTerm();
	}

	public FHIRDesignation(String language, String useSystem, String useCode, String value) {
		this.language = language;
		this.value = value;
		setUse(useSystem, useCode);
	}

	public FHIRDesignation(String language, String value) {
		this.language = language;
		this.value = value;
	}

	public FHIRDesignation(CodeSystem.ConceptDefinitionDesignationComponent designation) {
		language = designation.getLanguage();
		value = designation.getValue();
		setUse(designation.getUse());
		designation.getExtension().forEach( ext -> {
			if (extensions == null){
				extensions = new ArrayList<>();
			}
			extensions.add(new FHIRExtension(ext));
		});
	}

	public FHIRDesignation(ValueSet.ConceptReferenceDesignationComponent designation) {
		language = designation.getLanguage();
		value = designation.getValue();
		setUse(designation.getUse());
		designation.getExtension().forEach( ext -> {
			if (extensions == null){
				extensions = new ArrayList<>();
			}
			extensions.add(new FHIRExtension(ext));
		});
	}

	public void setUse(Coding useCoding) {
		setUse(useCoding.getSystem(), useCoding.getCode());
	}

	public void setUse(String useSystem, String useCode) {
		if(useSystem == null && useCode == null){
			use = null;
		} else {
			use = useSystem + "|" + useCode;
		}
	}

	public Coding getUseCoding() {
		if (use != null) {
			if (use.contains("|")) {
				String[] split = use.split("\\|");
				return addKnownDisplays(new Coding(split[0], split[1], null));
			} else {
				return new Coding(null, use, null);
			}
		}
		return null;
	}

	public ValueSet.ConceptReferenceDesignationComponent getHapi() {
		ValueSet.ConceptReferenceDesignationComponent hapiConceptReferenceDesignationComponent = new ValueSet.ConceptReferenceDesignationComponent();
		hapiConceptReferenceDesignationComponent.setLanguage(language);
		hapiConceptReferenceDesignationComponent.setValue(value);
		if (StringUtils.isNotEmpty(use)) {
			hapiConceptReferenceDesignationComponent.setUse(this.getUseCoding());
		}
		hapiConceptReferenceDesignationComponent.setExtension(Optional.ofNullable(extensions).orElse(Collections.emptyList()).stream().map(d->d.getHapi()).toList());
		return hapiConceptReferenceDesignationComponent;
	}

	private static Coding addKnownDisplays(Coding coding) {
		if (coding != null) {
			if (SNOMED_URI.equals(coding.getSystem())) {
				if ("900000000000003001".equals(coding.getCode())) {
					coding.setDisplay("Fully specified name");
				} else if ("900000000000013009".equals(coding.getCode())) {
					coding.setDisplay("Synonym");
				} else if ("900000000000550004".equals(coding.getCode())) {
					coding.setDisplay("Text definition");
				} else if ("900000000000548007".equals(coding.getCode())) {
					coding.setDisplay("PREFERRED");
				} else if ("900000000000549004".equals(coding.getCode())) {
					coding.setDisplay("ACCEPTABLE");
				}
			}
		}
		return coding;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getUse() {
		return use;
	}

	public void setUse(String use) {
		this.use = use;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public List<FHIRExtension> getExtensions() {
		return extensions;
	}

	public void setExtensions(List<FHIRExtension> extensions) {
		this.extensions = extensions;
	}
}
