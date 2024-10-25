package org.snomed.snowstorm.fhir.pojo;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;

public record ConceptAndSystemResult(Concept concept, FHIRCodeSystemVersion codeSystemVersion, String message, boolean postcoordinated) {

	public ConceptAndSystemResult(Concept concept, FHIRCodeSystemVersion codeSystemVersion) {
		this(concept, codeSystemVersion, null, false);
	}
}
