package org.snomed.snowstorm.fhir.pojo;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;

public class ConceptAndSystemResult {

	private final Concept concept;
	private final FHIRCodeSystemVersion codeSystemVersion;

	public ConceptAndSystemResult(Concept concept, FHIRCodeSystemVersion codeSystemVersion) {
		this.concept = concept;
		this.codeSystemVersion = codeSystemVersion;
	}

	public Concept getConcept() {
		return concept;
	}

	public FHIRCodeSystemVersion getCodeSystemVersion() {
		return codeSystemVersion;
	}
}
