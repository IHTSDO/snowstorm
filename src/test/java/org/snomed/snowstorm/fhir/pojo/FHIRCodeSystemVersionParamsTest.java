package org.snomed.snowstorm.fhir.pojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemVersionParamsTest {

	@Test
	void isSnomed() {
		assertTrue(new FHIRCodeSystemVersionParams("http://snomed.info/sct").isSnomed());
		assertTrue(new FHIRCodeSystemVersionParams("http://snomed.info/sct/900000000000207008").isSnomed());
		assertTrue(new FHIRCodeSystemVersionParams("http://snomed.info/sct/32506021000036107/version/20130531").isSnomed());
		assertTrue(new FHIRCodeSystemVersionParams("http://snomed.info/xsct").isSnomed());
		assertFalse(new FHIRCodeSystemVersionParams("http://hl7.org/fhir/sid/icd-10").isSnomed());
	}

	@Test
	void isSnomedUnversioned() {
		assertFalse(new FHIRCodeSystemVersionParams("http://snomed.info/sct").isUnversionedSnomed());
		assertFalse(new FHIRCodeSystemVersionParams("http://snomed.info/sct/900000000000207008").isUnversionedSnomed());
		assertFalse(new FHIRCodeSystemVersionParams("http://snomed.info/sct/32506021000036107/version/20130531").isUnversionedSnomed());
		assertTrue(new FHIRCodeSystemVersionParams("http://snomed.info/xsct").isUnversionedSnomed());
		assertFalse(new FHIRCodeSystemVersionParams("http://hl7.org/fhir/sid/icd-10").isUnversionedSnomed());
	}

	@Test
	void toSnomedUri() {
		assertNull(new FHIRCodeSystemVersionParams("http://hl7.org/fhir/sid/icd-10").toSnomedUri());
		assertEquals("http://snomed.info/sct/900000000000207008/version/20220131",
				new FHIRCodeSystemVersionParams("http://snomed.info/sct/900000000000207008", "20220131").toSnomedUri().toString());
	}
}