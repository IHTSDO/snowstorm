package org.snomed.snowstorm.fhir.pojo;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.fhir.services.FHIRHelper;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemVersionParamsTest {

	@Test
	void isSnomed() {
		assertTrue(getParams("http://snomed.info/sct", null).isSnomed());
		assertTrue(getParams("http://snomed.info/sct", "http://snomed.info/sct/900000000000207008").isSnomed());
		assertTrue(getParams("http://snomed.info/sct", "http://snomed.info/sct/32506021000036107/version/20130531").isSnomed());
		assertTrue(getParams("http://snomed.info/xsct", null).isSnomed());
		assertFalse(getParams("http://hl7.org/fhir/sid/icd-10", null).isSnomed());
	}

	@Test
	void isSnomedUnversioned() {
		assertFalse(getParams("http://snomed.info/sct", null).isUnversionedSnomed());
		assertFalse(getParams("http://snomed.info/sct", "http://snomed.info/sct/900000000000207008").isUnversionedSnomed());
		assertFalse(getParams("http://snomed.info/sct", "http://snomed.info/sct/32506021000036107/version/20130531").isUnversionedSnomed());
		assertTrue(getParams("http://snomed.info/xsct", null).isUnversionedSnomed());
		assertTrue(getParams("http://snomed.info/xsct", "http://snomed.info/xsct/900000000000207008").isUnversionedSnomed());
		assertFalse(getParams("http://hl7.org/fhir/sid/icd-10", null).isUnversionedSnomed());
	}

	@Test
	void toSnomedUri() {
		assertNull(getParams("http://hl7.org/fhir/sid/icd-10", null).toSnomedUri());
		assertEquals("http://snomed.info/sct/900000000000207008/version/20220131",
				getParams("http://snomed.info/sct", "http://snomed.info/sct/900000000000207008/version/20220131").toSnomedUri().toString());
	}

	private FHIRCodeSystemVersionParams getParams(String system, String version) {
		return FHIRHelper.getCodeSystemVersionParams(null, system, version, null);
	}
}