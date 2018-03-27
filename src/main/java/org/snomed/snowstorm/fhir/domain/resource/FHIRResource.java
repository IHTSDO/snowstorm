package org.snomed.snowstorm.fhir.domain.resource;

import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.element.FHIRBackboneElement;

public abstract class FHIRResource extends FHIRBackboneElement implements FHIRConstants {
	String resourceType = this.getClass().getSimpleName().replaceAll(FHIR, "");
}
