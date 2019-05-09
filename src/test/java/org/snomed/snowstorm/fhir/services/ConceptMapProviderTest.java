package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class ConceptMapProviderTest extends AbstractFHIRTest {
	
	@Autowired
	protected ReferenceSetMemberService memberService;
	
	@Before
	synchronized public void setup() throws ServiceException, InterruptedException {
		if (!setupComplete) {
			super.setup();
		}
		
		ReferenceSetMember member = new ReferenceSetMember(null, "900000000000527005", sampleSCTID);
		member.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, "88189002");
		memberService.createMember(MAIN, member);
	}
	
	@Test
	public void testHistoricAssociation() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir//ConceptMap/$translate?code=" + sampleSCTID + "&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs&url=http://snomed.info/sct?fhir_cm=900000000000527005";
		Parameters parameters = get(url);
		assertNotNull(parameters);
		Type t = parameters.getParameter("result");
		assertTrue(t.castToBoolean(t).booleanValue());
	}

	private Parameters get(String url) throws FHIROperationException {
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		checkForError(response);
		return fhirJsonParser.parseResource(Parameters.class, response.getBody());
	}
	
}
