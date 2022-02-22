package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.jpa.entity.TermConceptDesignation;

public class FHIRDesignation {

	private String language;
	private String use;
	private String value;

	public FHIRDesignation() {
	}

	public FHIRDesignation(TermConceptDesignation designation) {
		language = designation.getLanguage();
		use = designation.getUseCode();
		value = designation.getValue();
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
