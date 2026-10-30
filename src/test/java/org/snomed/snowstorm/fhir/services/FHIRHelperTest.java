package org.snomed.snowstorm.fhir.services;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;

import java.util.List;

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

	@Test
	void testGetVersionParamsSCTByIdWithMatchingModuleAndVersion() {
		FHIRCodeSystemVersionParams versionParams = FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_20241130", "http://snomed.info/sct", "http://snomed.info/sct/11000221109/version/20241130", null);
		assertEquals("11000221109", versionParams.getSnomedModule());
		assertEquals("20241130", versionParams.getVersion());
	}

	@Test
	void testGetVersionParamsSCTByIdNonSnomedSystemKeepsId() {
		FHIRCodeSystemVersionParams versionParams =
				FHIRHelper.getCodeSystemVersionParams("my-id", "http://example.com/cs", null, null);
		assertFalse(versionParams.isSnomed());
		assertEquals("my-id", versionParams.getId());
	}

	@Test
	void testGetVersionParamsSCTByIdMalformed() {
		for (String badId : List.of("sct_abc_20241130", "sct_11000221109", "sct_11000221109_2024113", "sct_11000221109_202411301", "foo")) {
			assertThrows(SnowstormFHIRServerResponseException.class,
					() -> FHIRHelper.getCodeSystemVersionParams(badId, "http://snomed.info/sct", null, null), badId);
		}
	}

	@Test
	void testGetVersionParamsSCTByIdModuleMismatch() {
		assertThrows(SnowstormFHIRServerResponseException.class, () -> FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_20241130", "http://snomed.info/sct", "http://snomed.info/sct/900000000000207008", null));
	}

	@Test
	void testGetVersionParamsSCTByIdVersionMismatch() {
		assertThrows(SnowstormFHIRServerResponseException.class, () -> FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_20241130", "http://snomed.info/sct", "http://snomed.info/sct/11000221109/version/20250101", null));
	}

	@Test
	void testGetVersionParamsNoSystemOrId() {
		assertThrows(SnowstormFHIRServerResponseException.class, () -> FHIRHelper.getCodeSystemVersionParams((String) null, null, null, null));
	}

}
