package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concepts;

import static org.junit.Assert.assertEquals;

class CodeSystemProviderSubsumesTest extends AbstractFHIRTest {
	
	@Test
	void testSubsumption() throws FHIROperationException {
		String URI = "http://snomed.info/sct/" + sampleModuleId + "/version/" + sampleVersion;
		
		//Test subsumption using defaults
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$subsumes?version=" + URI + "&codeA=" + Concepts.SNOMEDCT_ROOT +"&codeB=" + sampleSCTID;
		Parameters p = get(url);
		String result = toString(getProperty(p, "outcome"));
		assertEquals("subsumes", result);
		
		//Test reverse subsumption using defaults
		url = "http://localhost:" + port + "/fhir/CodeSystem/$subsumes?version=" + URI + "&codeB=" + Concepts.SNOMEDCT_ROOT +"&codeA=" + sampleSCTID;
		p = get(url);
		result = toString(getProperty(p, "outcome"));
		assertEquals("subsumed-by", result);
		
		//Alternative URLs using coding saying the same thing
		url = "http://localhost:" + port + "/fhir/CodeSystem/$subsumes?codingA=" + URI + "|" + Concepts.SNOMEDCT_ROOT +"&codingB=" + URI + "|"  + sampleSCTID;
		p = get(url);
		result = toString(getProperty(p, "outcome"));
		assertEquals("subsumes", result);
	}
	
}
