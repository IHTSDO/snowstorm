package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concepts;

import java.util.List;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FHIRConceptMapProviderTest extends AbstractFHIRTest {
	
	@Test
	void testHistoricAssociation() {
		String vs = "http://snomed.info/sct?fhir_cm=" + Concepts.REFSET_SAME_AS_ASSOCIATION;
		String url = "http://localhost:" + port + "/fhir/ConceptMap/$translate?code=" + sampleSCTID + "&system=http://snomed.info/sct&url=" + vs;
		Parameters parameters = getParameters(url);
		assertNotNull(parameters);
		assertTrue(parameters.getParameterBool("result"));

		// Use xsct to access daily build
		vs = "http://snomed.info/xsct?fhir_cm=" + Concepts.REFSET_SAME_AS_ASSOCIATION;
		url = "http://localhost:" + port + "/fhir/ConceptMap/$translate?code=" + sampleSCTID + "&system=http://snomed.info/xsct&url=" + vs;
		parameters = getParameters(url);
		assertNotNull(parameters);
		assertTrue(parameters.getParameterBool("result"));

		// Should also work with a specific module
		vs = "http://snomed.info/xsct/1234000008?fhir_cm=" + Concepts.REFSET_SAME_AS_ASSOCIATION;
		url = "http://localhost:" + port + "/fhir/ConceptMap/$translate?code=" + sampleSCTID + "&system=http://snomed.info/sct&url=" + vs;
		parameters = getParameters(url);
		assertNotNull(parameters);
		assertTrue(parameters.getParameterBool("result"));
	}

	@Test
	void testICDMap() {
		String expectBodyContains = "A1.100";
		Parameters parameters = getParameters(baseUrl + "/ConceptMap/$translate?" +
				"code=" + sampleSCTID +
				"&system=http://snomed.info/sct" +
				"&targetsystem=http://hl7.org/fhir/sid/icd-10",
				200, expectBodyContains);
		assertNotNull(parameters);
		assertTrue(parameters.getParameterBool("result"));

		getParameters(baseUrl + "/ConceptMap/$translate?" +
				"code=1000" +
				"&system=http://snomed.info/sct" +
				"&targetsystem=http://hl7.org/fhir/sid/icd-10",
				200, "No mapping found for code");
	}
	
}
