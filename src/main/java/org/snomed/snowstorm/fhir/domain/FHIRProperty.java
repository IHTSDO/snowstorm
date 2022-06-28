package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.jpa.entity.TermConceptProperty;
import ca.uhn.fhir.jpa.entity.TermConceptPropertyTypeEnum;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;

public class FHIRProperty {

	private String code;
	private String display;
	private String value;
	private String type;

	public FHIRProperty() {
	}

	public FHIRProperty(TermConceptProperty property) {
		code = property.getKey();
		display = property.getDisplay();
		value = property.getValue();
		TermConceptPropertyTypeEnum enumType = property.getType();
		type = enumType != null ? enumType.name() : TermConceptPropertyTypeEnum.CODING.name();
	}

	public Type toHapiValue(String systemVersionUrl) {
		if (TermConceptPropertyTypeEnum.STRING.name().equals(type)) {
			return new StringType(value);
		} else {
			return new Coding(systemVersionUrl, getValue(), getDisplay());
		}
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

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
