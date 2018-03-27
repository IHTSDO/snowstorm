package org.snomed.snowstorm.fhir.domain.element;

import java.net.URI;

import org.snomed.snowstorm.fhir.domain.resource.FHIRResource;

public class FHIREntry extends FHIRBackboneElement {

	URI fullUrl;
	FHIRResource resource;
	
	public FHIREntry (FHIRResource resource) {
		this.resource = resource;
	}
}
