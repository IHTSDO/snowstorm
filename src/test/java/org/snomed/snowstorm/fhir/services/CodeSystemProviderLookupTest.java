package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertNotNull;

class CodeSystemProviderLookupTest extends AbstractFHIRTest {

	@Test
	void testSingleConceptRecovery() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&_format=json";
		Parameters p = get(url);
		assertNotNull(p);
	}
	
	@Test
	void testSinglePropertiesRecovery() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&_format=json";
		Parameters p = get(url);
		/*for (ParametersParameterComponent parameter : p.getParameter()) {
			logger.info(toString(parameter, ""));
		}*/
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
	}

	@Test
	void testMultipleConceptPropertiesRecovery() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = get(url);
		
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
		
		String sdProperty = toString(getProperty(p, "sufficientlyDefined"));
		assertNotNull(sdProperty);
	}
	
}
