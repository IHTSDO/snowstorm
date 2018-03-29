package org.snomed.snowstorm.fhir.domain.element;

import java.util.ArrayList;
import java.util.List;

import org.snomed.snowstorm.fhir.domain.resource.FHIRResource;
import org.snomed.snowstorm.rest.View;

import com.fasterxml.jackson.annotation.JsonView;

public class FHIRParameter {
	@JsonView(value = View.Component.class)
	String name;
	
	@JsonView(value = View.Component.class)
	String valueCode;
	
	@JsonView(value = View.Component.class)
	FHIRCoding valueCoding;
	
	@JsonView(value = View.Component.class)
	String valueString;
	
	@JsonView(value = View.Component.class)
	Boolean valueBoolean;
	
	@JsonView(value = View.Component.class)
	FHIRResource resource;
	
	@JsonView(value = View.Component.class)
	List<FHIRParameter> part;
	
	public FHIRParameter (String name) {
		this.name = name;
	}
	
	public FHIRParameter (String name, FHIRCoding value) {
		this.name = name;
		this.valueCoding = value;
	}
	
	public FHIRParameter (String name, FHIRResource resource) {
		this.name = name;
		this.resource = resource;
	}
	
	public FHIRParameter (String name, String value, boolean isCode) {
		this.name = name;
		if (isCode) {
			this.valueCode = value;
		} else {
			this.valueString = value;
		}
	}
	
	public FHIRParameter (String name, List<FHIRParameter> parts) {
		this.name = name;
		this.part = parts;
	}
	
	public FHIRParameter(String name, Object value) {
		this.name = name;
		if (value instanceof String) {
			this.valueString = (String)value;
		}else if (value instanceof Boolean) {
			this.valueBoolean = (Boolean) value;
		} else if (value instanceof FHIRCoding) {
			this.valueCoding = (FHIRCoding) value;
		}
	}

	public FHIRParameter addPart (FHIRParameter part) {
		if (this.part == null) {
			this.part = new ArrayList<>();
		}
		this.part.add(part);
		return this;
	}

	public void setValue(String value) {
		this.valueString = value;
	}
}
