package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concepts;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConceptMapProviderTest extends AbstractFHIRTest {
	
	@Test
	void testHistoricAssociation() throws FHIROperationException {
		//We're using MAIN so pass the unversioned version
		String vs = "http://snomed.info/sct/900000000000207008/version/UNVERSIONED?fhir_cm=" + Concepts.REFSET_SAME_AS_ASSOCIATION;
		String sourceTarget = "source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs";
		String url = "http://localhost:" + port + "/fhir/ConceptMap/$translate?code=" + sampleSCTID + "&system=http://snomed.info/sct&" + sourceTarget + "&url=" + vs;
		Parameters parameters = getParameters(url);
		assertNotNull(parameters);
		Type t = parameters.getParameter("result");
		assertTrue(t.castToBoolean(t).booleanValue());
		
		//We can also use the xsct form to specify unpublished content
		vs = "http://snomed.info/xsct?fhir_cm=" + Concepts.REFSET_SAME_AS_ASSOCIATION;
		url = "http://localhost:" + port + "/fhir/ConceptMap/$translate?code=" + sampleSCTID + "&system=http://snomed.info/sct&" + sourceTarget + "&url=" + vs;
		parameters = getParameters(url);
		assertNotNull(parameters);
		t = parameters.getParameter("result");
		assertTrue(t.castToBoolean(t).booleanValue());
	}
	
}
