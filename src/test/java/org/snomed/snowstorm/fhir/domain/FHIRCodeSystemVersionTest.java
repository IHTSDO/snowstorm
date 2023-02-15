package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemVersionTest {

	@Test
	void testIdGen() {
		FHIRCodeSystemVersion version = new FHIRCodeSystemVersion(new CodeSystem().setUrl("http://hl7.org/fhir/sid/icd-10-cm_2"));
		assertEquals("hl7.org-fhir-sid-icd-10-cm-2", version.getId(),
				"ID generated from URL should only contain a-z, A-Z, 0-9, '-' or '.'");
	}
}
