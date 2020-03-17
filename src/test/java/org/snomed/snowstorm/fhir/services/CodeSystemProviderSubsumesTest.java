package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class CodeSystemProviderSubsumesTest extends AbstractFHIRTest {
	
	@Test
	public void testSubsumption() throws FHIROperationException {
		//Test subsumption using defaults
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$subsumes?codeA=" + Concepts.SNOMEDCT_ROOT +"&codeB=" + sampleSCTID;
		Parameters p = get(url);
		String result = toString(getProperty(p, "outcome"));
		assertEquals("subsumes", result);
		
		//Test reverse subsumption using defaults
		url = "http://localhost:" + port + "/fhir/CodeSystem/$subsumes?codeB=" + Concepts.SNOMEDCT_ROOT +"&codeA=" + sampleSCTID;
		p = get(url);
		result = toString(getProperty(p, "outcome"));
		assertEquals("subsumed-by", result);
		
		//Alternative URLs using coding saying the same thing
		String URI = "http://snomed.info/sct/" + sampleModuleId + "/version/" + sampleVersion;
		url = "http://localhost:" + port + "/fhir/CodeSystem/$subsumes?codingA=" + URI + "|" + Concepts.SNOMEDCT_ROOT +"&codingB=" + URI + "|"  + sampleSCTID;
		p = get(url);
		result = toString(getProperty(p, "outcome"));
		assertEquals("subsumes", result);
	}
	
}
