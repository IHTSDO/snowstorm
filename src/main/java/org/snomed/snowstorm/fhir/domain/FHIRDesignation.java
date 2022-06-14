package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.jpa.entity.TermConceptDesignation;
import org.hl7.fhir.r4.model.Coding;
import org.snomed.snowstorm.core.data.domain.Description;

import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

public class FHIRDesignation {

	private String language;
	private String use;
	private String value;

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

	public void setUse(String useSystem, String useCode) {
		use = useSystem + "|" + useCode;
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
}
