package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.jpa.entity.TermConceptProperty;
import ca.uhn.fhir.jpa.entity.TermConceptPropertyTypeEnum;
import org.hl7.fhir.r4.model.*;

public class FHIRProperty {

	public static final String STRING = "STRING";
	public static final String CODING = "CODING";
	public static final String CODE = "CODE";

	private String code;
	private String display;
	private String value;
	private String type;

	public FHIRProperty() {
	}

	public FHIRProperty(String code, String display, String value, String type) {
		this.code = code;
		this.display = display;
		this.value = value;
		this.type = type;
	}

	public FHIRProperty(TermConceptProperty property) {
		code = property.getKey();
		display = property.getDisplay();
		value = property.getValue();
		TermConceptPropertyTypeEnum enumType = property.getType();
		type = enumType != null ? enumType.name() : CODING;
	}

	public FHIRProperty(CodeSystem.ConceptPropertyComponent propertyComponent) {
		code = propertyComponent.getCode();
		if (propertyComponent.hasValueCoding()) {
			Coding valueCoding = propertyComponent.getValueCoding();
			value = valueCoding.getCode();
			display = valueCoding.getDisplay();
			type = CODING;
		} else if (propertyComponent.hasValueCodeType()) {
			value = propertyComponent.getValueCodeType().getValue();
			type = CODE;
		} else if (propertyComponent.hasValueStringType()) {
			value = propertyComponent.getValueStringType().getValue();
			type = STRING;
		}
	}

	public Type toHapiValue(String systemVersionUrl) {
		if (STRING.equals(type)) {
			return new StringType(value);
		} else if (CODE.equals(type)) {
			return new CodeType(value);
		} else if (CODING.equals(type)) {
			return new Coding(systemVersionUrl, value, display);
		}
		return null;
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
