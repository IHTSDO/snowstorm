package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemProviderLookupTest extends AbstractFHIRTest {

	@Test
	void testSingleConceptRecovery() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&_format=json";
		assertLookupSystemAndVersion(url, "http://snomed.info/sct/900000000000207008/version/20190131");
	}

	@Test
	void testSingleConceptLookupInternationalSnomedVersion() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct" +
				"&version=http://snomed.info/sct/900000000000207008&code=" + sampleSCTID + "&_format=json";
		assertLookupSystemAndVersion(url, "http://snomed.info/sct/900000000000207008/version/20190131");
	}

	@Test
	void testSingleConceptLookupExtensionSnomedVersionByVersionParam() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct" +
				"&version=http://snomed.info/sct/1234000008&code=" + sampleSCTID + "&_format=json";
		assertLookupSystemAndVersion(url, "http://snomed.info/sct/1234000008/version/20190731");
	}

	@Test
	void testSingleConceptLookupExtensionSnomedVersionByInstanceUrl() {
		String url = baseUrl + "/CodeSystem/sct_1234000008_20190731/$lookup?code=" + sampleSCTID + "&_format=json";
		assertLookupSystemAndVersion(url, "http://snomed.info/sct/1234000008/version/20190731");
	}

	@Test
	void testSingleConceptLookupByUnversionedInstanceUrl() {
		String url = baseUrl + "/CodeSystem/sct_" + sampleModuleId + "_UNVERSIONED/$lookup?code=" + sampleSCTID + "&_format=json";
		Parameters p = getParameters(url);
		assertEquals("http://snomed.info/xsct/" + sampleModuleId, ((PrimitiveType<?>) p.getParameterValue("version")).getValueAsString());
	}

	@Test
	void testValidateCodeByInstanceUrlWithMatchingVersionParam() {
		String url = baseUrl + "/CodeSystem/sct_1234000008_20190731/$validate-code?code=" + sampleSCTID +
				"&version=http://snomed.info/sct/1234000008/version/20190731&_format=json";
		Parameters p = getParameters(url);
		assertEquals("true", ((PrimitiveType<?>) p.getParameterValue("result")).getValueAsString());
	}

	@Test
	void testValidateCodeByInstanceUrlWithDateOnlyVersion() {
		String url = baseUrl + "/CodeSystem/sct_1234000008_20190731/$validate-code?code=" + sampleSCTID + "&version=20190731&_format=json";
		getParameters(url, 400, "must use the format");
	}

	@Test
	void testValidateCodeByInstanceUrlWithMismatchedSystem() {
		String url = baseUrl + "/CodeSystem/sct_1234000008_20190731/$validate-code?url=http://loinc.org&code=" + sampleSCTID + "&_format=json";
		getParameters(url, 400, "is not a SNOMED CT system");
	}

	private void assertLookupSystemAndVersion(String url, String expectedVersion) {
		Parameters p = getParameters(url);
		assertNotNull(p);
		assertEquals("http://snomed.info/sct", ((PrimitiveType<?>) p.getParameterValue("system")).getValueAsString());
		assertEquals(expectedVersion, ((PrimitiveType<?>) p.getParameterValue("version")).getValueAsString());
	}

	@Test
	void testSinglePropertiesRecovery() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&_format=json";
		Parameters p = getParameters(url);
		/*for (ParametersParameterComponent parameter : p.getParameter()) {
			logger.info(toString(parameter, ""));
		}*/
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
	}

	@Test
	void testMultipleConceptPropertiesRecovery() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
		
		String sdProperty = toString(getProperty(p, "sufficientlyDefined"));
		assertNotNull(sdProperty);
	}

	@Test
	void testParameterActiveWhenActive() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		Boolean inactive = toBoolean(getProperty(p, "inactive"));
		assertFalse(inactive);
	}

	@Test
	void testParameterActiveWhenInactive() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleInactiveSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		Boolean inactive = toBoolean(getProperty(p, "inactive"));
		assertTrue(inactive);
	}
	
	@Test
	void testSingleUnpublishedConceptRecovery() {
		String version = "version=http://snomed.info/xsct/" + sampleModuleId;
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/xsct&code=" + sampleSCTID + "&" + version;
		Parameters p = getParameters(url);
		assertNotNull(p);
	}
	
}
