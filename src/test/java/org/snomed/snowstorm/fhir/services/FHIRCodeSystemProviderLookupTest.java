package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemProviderLookupTest extends AbstractFHIRTest {

	@Test
	void testSingleConceptLookupDefaultSnomedVersion() {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&_format=json";
		Parameters p = getParameters(url);
		assertNotNull(p);
		assertEquals("http://snomed.info/sct", p.getParameterValue("system").toString());
		assertEquals("http://snomed.info/sct/900000000000207008/version/20190131", p.getParameterValue("version").toString());
	}

	@Test
	void testSingleConceptLookupInternationalSnomedVersion() {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct" +
				"&version=http://snomed.info/sct/900000000000207008&code=" + sampleSCTID + "&_format=json";
		Parameters p = getParameters(url);
		assertNotNull(p);
		assertEquals("http://snomed.info/sct", p.getParameterValue("system").toString());
		assertEquals("http://snomed.info/sct/900000000000207008/version/20190131", p.getParameterValue("version").toString());
	}

	@Test
	void testSingleConceptLookupExtensionSnomedVersionByVersionParam() {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct" +
				"&version=http://snomed.info/sct/1234000008&code=" + sampleSCTID + "&_format=json";
		Parameters p = getParameters(url);
		assertNotNull(p);
		assertEquals("http://snomed.info/sct", p.getParameterValue("system").toString());
		assertEquals("http://snomed.info/sct/1234000008/version/20190731", p.getParameterValue("version").toString());
	}
	
	@Test
	void testSingleConceptLookupExtensionSnomedVersionByInstanceUrl() {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/sct_1234000008_20190731/$lookup?code=" + sampleSCTID + "&_format=json";
		Parameters p = getParameters(url);
		assertNotNull(p);
		assertEquals("http://snomed.info/sct", p.getParameterValue("system").toString());
		assertEquals("http://snomed.info/sct/1234000008/version/20190731", p.getParameterValue("version").toString());
	}

	@Test
	void testSinglePropertiesRecovery() {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&_format=json";
		Parameters p = getParameters(url);
		/*for (ParametersParameterComponent parameter : p.getParameter()) {
			logger.info(toString(parameter, ""));
		}*/
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
	}

	@Test
	void testMultipleConceptPropertiesRecovery() {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
		
		String sdProperty = toString(getProperty(p, "sufficientlyDefined"));
		assertNotNull(sdProperty);
	}

	@Test
	void testParameterActiveWhenActive() {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		Boolean inactive = toBoolean(getProperty(p, "inactive"));
		assertFalse(inactive);
	}

	@Test
	void testParameterActiveWhenInactive() {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleInactiveSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		Boolean inactive = toBoolean(getProperty(p, "inactive"));
		assertTrue(inactive);
	}
	
	@Test
	void testSingleUnpublishedConceptRecovery() {
		String version = "version=http://snomed.info/xsct/" + sampleModuleId;
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/xsct&code=" + sampleSCTID + "&" + version;
		Parameters p = getParameters(url);
		assertNotNull(p);
	}
	
}
