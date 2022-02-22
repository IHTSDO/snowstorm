package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.jpa.entity.TermConceptProperty;

public class FHIRProperty {

	private String code;
	private String description;
	private String value;

	public FHIRProperty() {
	}

	public FHIRProperty(TermConceptProperty property) {
		code = property.getKey();
		description = property.getDisplay();
		value = property.getValue();
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
