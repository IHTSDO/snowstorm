package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concepts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

class CodeSystemProviderSubsumesTest extends AbstractFHIRTest {
	
	@Test
	void testSubsumption() throws FHIROperationException {
		String version = "http://snomed.info/sct" + "/" + sampleModuleId + "/version/" + sampleVersion;
		
		//Test subsumption using defaults
		String url =
				"http://localhost:" + port + "/fhir/CodeSystem/$subsumes?system=http://snomed.info/sct" + "&version=" + version + "&codeA=" + Concepts.SNOMEDCT_ROOT +"&codeB=" + sampleSCTID;
		Parameters p = getParameters(url);
		String result = toString(getProperty(p, "outcome"));
		assertEquals("subsumes", result);
		
		//Test reverse subsumption using defaults
		url = "http://localhost:" + port + "/fhir/CodeSystem/$subsumes?system=http://snomed.info/sct" + "&version=" + version + "&codeB=" + Concepts.SNOMEDCT_ROOT +"&codeA=" + sampleSCTID;
		p = getParameters(url);
		result = toString(getProperty(p, "outcome"));
		assertEquals("subsumed-by", result);
		
		// Alternative URLs using coding, system param is missing - should fail
		url = "http://localhost:" + port + "/fhir/CodeSystem/$subsumes?version=" + version + "&codingA=" + SNOMED_URI + "|" + Concepts.SNOMEDCT_ROOT + "&codingB=" + SNOMED_URI + "|" + sampleSCTID;
		getParameters(url, 400, "One of id or system parameters must be supplied");
	}
	
}
