package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeSystemProviderLookupTest extends AbstractFHIRTest {

	@Test
	void testSingleConceptRecovery() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&_format=json";
		Parameters p = getParameters(url);
		assertNotNull(p);
	}
	
	@Test
	void testSinglePropertiesRecovery() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&_format=json";
		Parameters p = getParameters(url);
		/*for (ParametersParameterComponent parameter : p.getParameter()) {
			logger.info(toString(parameter, ""));
		}*/
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
	}

	@Test
	void testMultipleConceptPropertiesRecovery() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
		
		String sdProperty = toString(getProperty(p, "sufficientlyDefined"));
		assertNotNull(sdProperty);
	}

	@Test
	void testParameterActiveWhenActive() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		Boolean active = toBoolean(getProperty(p, "active"));
		assertTrue(active);
	}

	@Test
	void testParameterActiveWhenInactive() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleInactiveSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		Boolean active = toBoolean(getProperty(p, "active"));
		assertFalse(active);
	}
	
	@Test
	void testSingleUnpublishedConceptRecovery() throws FHIROperationException {
		String version = "version=http://snomed.info/xsct/" + sampleModuleId;
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/xsct&code=" + sampleSCTID + "&" + version;
		Parameters p = getParameters(url);
		assertNotNull(p);
	}
	
}
