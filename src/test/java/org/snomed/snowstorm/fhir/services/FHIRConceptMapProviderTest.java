package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concepts;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FHIRConceptMapProviderTest extends AbstractFHIRTest {
	
	@Test
	void testHistoricAssociation() throws FHIROperationException {
		// Use xsct to access daily build
		String vs = "http://snomed.info/xsct?fhir_cm=" + Concepts.REFSET_SAME_AS_ASSOCIATION;
		String url = "http://localhost:" + port + "/fhir/ConceptMap/$translate?code=" + sampleSCTID + "&system=http://snomed.info/xsct&url=" + vs;
		Parameters parameters = getParameters(url);
		assertNotNull(parameters);
		Type t = parameters.getParameter("result");
		assertTrue(t.castToBoolean(t).booleanValue());

		// Should also work with a specific module
		vs = "http://snomed.info/xsct/1234000008?fhir_cm=" + Concepts.REFSET_SAME_AS_ASSOCIATION;
		url = "http://localhost:" + port + "/fhir/ConceptMap/$translate?code=" + sampleSCTID + "&system=http://snomed.info/sct&url=" + vs;
		parameters = getParameters(url);
		assertNotNull(parameters);
		t = parameters.getParameter("result");
		assertTrue(t.castToBoolean(t).booleanValue());
	}
	
}
