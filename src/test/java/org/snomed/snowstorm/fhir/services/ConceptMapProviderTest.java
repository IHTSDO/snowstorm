package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class ConceptMapProviderTest extends AbstractFHIRTest {
	
	@Test
	public void testHistoricAssociation() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ConceptMap/$translate?code=" + sampleSCTID + "&system=http://snomed.info/sct/1234&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct/1234?fhir_vs&url=http://snomed.info/sct?fhir_cm=" + Concepts.REFSET_SAME_AS_ASSOCIATION;
		Parameters parameters = get(url);
		assertNotNull(parameters);
		Type t = parameters.getParameter("result");
		assertTrue(t.castToBoolean(t).booleanValue());
	}
	
}
