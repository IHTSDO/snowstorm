package org.snomed.snowstorm.fhir.services;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;

import static org.junit.jupiter.api.Assertions.*;

class FHIRHelperTest {

	@Test
	void testGetVersionParamsSCT() {
		FHIRCodeSystemVersionParams versionParams =
				FHIRHelper.getCodeSystemVersionParams(null, "http://snomed.info/sct", null, null);
		assertTrue(versionParams.isSnomed());
		assertNull(versionParams.getSnomedModule());
		assertNull(versionParams.getVersion());
	}

	@Test
	void testGetVersionParamsSCTModule() {
		FHIRCodeSystemVersionParams versionParams =
				FHIRHelper.getCodeSystemVersionParams(null, "http://snomed.info/sct", "http://snomed.info/sct/11000221109", null);
		assertTrue(versionParams.isSnomed());
		assertEquals("11000221109", versionParams.getSnomedModule());
		assertNull(versionParams.getVersion());
	}

	@Test
	void testGetVersionParamsSCTModuleAndVersion() {
		FHIRCodeSystemVersionParams versionParams =
				FHIRHelper.getCodeSystemVersionParams(null, "http://snomed.info/sct", "http://snomed.info/sct/11000221109/version/20250101", null);
		assertTrue(versionParams.isSnomed());
		assertEquals("11000221109", versionParams.getSnomedModule());
		assertEquals("20250101", versionParams.getVersion());
	}

	@Test
	void testGetVersionParamsSCTById() {
		FHIRCodeSystemVersionParams versionParams =
				FHIRHelper.getCodeSystemVersionParams("sct_11000221109_20241130", null, null, null);
		assertTrue(versionParams.isSnomed());
		assertEquals("11000221109", versionParams.getSnomedModule());
		assertEquals("20241130", versionParams.getVersion());
	}

}
