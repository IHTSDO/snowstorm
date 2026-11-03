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
	void testGetVersionParamsSCTByIdWithMatchingVersionAndNoSystem() {
		FHIRCodeSystemVersionParams versionParams = FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_20241130", null, "http://snomed.info/sct/11000221109/version/20241130", null);
		assertTrue(versionParams.isSnomed());
		assertEquals("11000221109", versionParams.getSnomedModule());
		assertEquals("20241130", versionParams.getVersion());
	}

	@Test
	void testGetVersionParamsSCTByIdVersionMismatchAndNoSystem() {
		assertThrows(SnowstormFHIRServerResponseException.class, () -> FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_20241130", null, "http://snomed.info/sct/11000221109/version/20250101", null));
	}

	@Test
	void testGetVersionParamsSCTByIdDateOnlyVersion() {
		assertThrows(SnowstormFHIRServerResponseException.class, () -> FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_20241130", null, "20241130", null));
	}

	@Test
	void testGetVersionParamsNonSnomedIdKeepsLiteralVersion() {
		FHIRCodeSystemVersionParams versionParams = FHIRHelper.getCodeSystemVersionParams("my-id", null, "1.0.0", null);
		assertFalse(versionParams.isSnomed());
		assertEquals("my-id", versionParams.getId());
		assertEquals("1.0.0", versionParams.getVersion());
	}

	@Test
	void testGetVersionParamsSCTByIdNonSnomedSystem() {
		assertThrows(SnowstormFHIRServerResponseException.class, () -> FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_20241130", "http://loinc.org", null, null));
	}

	@Test
	void testGetVersionParamsSCTByIdNonSnomedSystemWithVersion() {
		SnowstormFHIRServerResponseException e = assertThrows(SnowstormFHIRServerResponseException.class, () -> FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_20241130", "http://loinc.org", "2.77", null));
		assertTrue(e.getMessage().contains("is not a SNOMED CT system"), e.getMessage());
	}

	@Test
	void testGetVersionParamsSCTByUnversionedIdWithPublishedVersionUri() {
		SnowstormFHIRServerResponseException e = assertThrows(SnowstormFHIRServerResponseException.class, () -> FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_UNVERSIONED_20241130", null, "http://snomed.info/sct/11000221109/version/20241130", null));
		assertTrue(e.getMessage().contains("do not agree"), e.getMessage());
	}

	@Test
	void testGetVersionParamsSCTByIdWithUnversionedVersionUri() {
		SnowstormFHIRServerResponseException e = assertThrows(SnowstormFHIRServerResponseException.class, () -> FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_20241130", null, "http://snomed.info/xsct/11000221109", null));
		assertTrue(e.getMessage().contains("do not agree"), e.getMessage());
	}

	@Test
	void testGetVersionParamsSCTByUnversionedIdWithMatchingUnversionedVersionUri() {
		FHIRCodeSystemVersionParams versionParams = FHIRHelper.getCodeSystemVersionParams(
				"sct_11000221109_UNVERSIONED", null, "http://snomed.info/xsct/11000221109", null);
		assertTrue(versionParams.isUnversionedSnomed());
		assertEquals("11000221109", versionParams.getSnomedModule());
	}

	@Test
	void testGetVersionParamsSCTByUnversionedId() {
		FHIRCodeSystemVersionParams versionParams =
				FHIRHelper.getCodeSystemVersionParams("sct_11000221109_UNVERSIONED", null, null, null);
		assertTrue(versionParams.isUnversionedSnomed());
		assertEquals("11000221109", versionParams.getSnomedModule());
		assertNull(versionParams.getVersion());
	}

	@Test
	void testGetVersionParamsSCTByUnversionedDatedId() {
		FHIRCodeSystemVersionParams versionParams =
				FHIRHelper.getCodeSystemVersionParams("sct_11000221109_UNVERSIONED_20241130", null, null, null);
		assertTrue(versionParams.isUnversionedSnomed());
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
