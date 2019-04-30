package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.Parameters.ParametersParameterComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class CodeSystemProviderTest extends AbstractFHIRTest {

	@Test
	public void testSingleConceptRecovery() {
		//String json = this.restTemplate.getForObject("http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + conceptId + "&_format=json", String.class);
		Parameters concept = this.restTemplate.getForObject("http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + conceptId + "&_format=json", Parameters.class);
		//String str = concept.toString();
		for (ParametersParameterComponent parameter : concept.getParameter()) {
			logger.info(toString(parameter, ""));
		}
		assertNotNull(concept);
	}
	
}
