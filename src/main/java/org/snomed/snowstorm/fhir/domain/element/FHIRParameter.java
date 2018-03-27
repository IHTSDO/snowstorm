package org.snomed.snowstorm.fhir.domain.element;

import java.util.List;

import org.snomed.snowstorm.fhir.domain.resource.FHIRResource;

public class FHIRParameter {
	String name;
	FHIRCoding valueCoding;
	String valueString;
	FHIRResource resource;
	List<FHIRParameter> part;
	
	public FHIRParameter (String name, FHIRCoding value) {
		this.name = name;
		this.valueCoding = value;
	}
	
	public FHIRParameter (String name, FHIRResource resource) {
		this.name = name;
		this.resource = resource;
	}
	
	public FHIRParameter (String name, String value) {
		this.name = name;
		this.valueString = value;
	}
	
	public FHIRParameter (String name, List<FHIRParameter> parts) {
		this.name = name;
		this.part = parts;
	}
}
