package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ValueSetProviderScrudTest extends AbstractFHIRTest {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Test
	void testValueSetCrudOperations() throws Exception {
		String testURL = "http://some.test";
		String testId = "test";
		ValueSet testVS = new ValueSet();
		testVS.setUrl(testURL);
		testVS.setId(testId);
		String vsJson = fhirJsonParser.encodeResourceToString(testVS);
		storeVs(testId, vsJson);
		
		//Now recover that ValueSet we just saved
		String url = baseUrl + "/" + testId;
		ResponseEntity<String> response2 = restTemplate.getForEntity(url, String.class);
		//Did we get an operation failed here?
		try {
			OperationOutcome oo = fhirJsonParser.parseResource(OperationOutcome.class, response2.getBody());
			throw new Exception ("Unexpected outcome: " + oo.toString());
		} catch (DataFormatException e) {
			// pass
		}
		ValueSet savedVS = fhirJsonParser.parseResource(ValueSet.class, response2.getBody());
		
		//Also check that attempting to recover a nonsense id gives us an HTTP 404 Not Found
		response2 = restTemplate.getForEntity(baseUrl + "/foo", String.class);
		assertEquals(HttpStatus.NOT_FOUND, response2.getStatusCode());
		
		//Next update the VS
		savedVS.setUrl("http://some.other.test");
		vsJson = fhirJsonParser.encodeResourceToString(savedVS);
		HttpEntity<String> request = new HttpEntity<>(vsJson, headers);
		response2 = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
		assertEquals(HttpStatus.OK, response2.getStatusCode());
		//ValueSet updated = fhirJsonParser.parseResource(ValueSet.class, response.getBody());
		
		//Now recover all valuesets
		response2 = restTemplate.getForEntity(baseUrl, String.class);
		assertEquals(HttpStatus.OK, response2.getStatusCode());
		assertNotNull(response2.getBody());
		Bundle bundle = fhirJsonParser.parseResource(Bundle.class, response2.getBody());
		if (bundle.getTotal() > 1) {
			logger.error("Found " +  bundle.getTotal() + " valuesets when expected 1");
		}
		assertEquals(1, bundle.getTotal());
		assertEquals(ResourceType.class, bundle.getResourceType().getClass());

		//And finally delete the vs
		url = baseUrl + "/" + testId;
		restTemplate.delete(url);
		
		//And prove it's no longer found
		request = new HttpEntity<>(vsJson, headers);
		response2 = restTemplate.getForEntity(url, String.class);
		assertEquals(HttpStatus.NOT_FOUND, response2.getStatusCode());
	
	}
	
	@Test
	void testValueSetExpansion() throws FHIROperationException {
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream is = classloader.getResourceAsStream("dummy-fhir-content/exampleVS_ECL.json");
		assertNotNull(is);
		ValueSet exampleVS = fhirJsonParser.parseResource(ValueSet.class, is);
		String vsJson = fhirJsonParser.encodeResourceToString(exampleVS);
		logger.info("Saving chronic-diseases");
		storeVs("chronic-diseases", vsJson);
		
		//Now expand that ValueSet we just saved
		String url = baseUrl + "/chronic-diseases/$expand";
		ValueSet savedVS = getValueSet(url);
		assertEquals(0, savedVS.getExpansion().getTotal());
		
		restTemplate.delete(baseUrl + "/chronic-diseases");
		logger.info("ValueSet expansion tested OK!");
	}

	private ValueSet getValueSet(String url) throws FHIROperationException {
		ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
		checkForError(response);
		return fhirJsonParser.parseResource(ValueSet.class, response.getBody());
	}

	private void storeVs(String id, String vsJson) {
		HttpEntity<String> request = new HttpEntity<>(vsJson, headers);
		ResponseEntity<MethodOutcome> response = restTemplate.exchange(baseUrl + "/" + id, HttpMethod.PUT, request, MethodOutcome.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		MethodOutcome outcome = response.getBody();
		assertNull(outcome);
	}

	@Test
	void testValueSetExample() {
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream is = classloader.getResourceAsStream("dummy-fhir-content/exampleVS.json");
		assertNotNull(is);
		ValueSet exampleVS = fhirJsonParser.parseResource(ValueSet.class, is);
		String vsJson = fhirJsonParser.encodeResourceToString(exampleVS);
		storeVs("address-use", vsJson);
		
		//Now recover that ValueSet we just saved
		String url = baseUrl + "/address-use";
		ResponseEntity<String> response2 = restTemplate.getForEntity(url, String.class);
		ValueSet savedVS = fhirJsonParser.parseResource(ValueSet.class, response2.getBody());
		restTemplate.delete(url );
		assertNotNull(savedVS);
	}
	
	@Test
	void testValueSetSearchWithCode() {
		//We do not allow expanding all ValueSets to search for a concept - too costly
		String url = baseUrl + "?code=foo";
		ResponseEntity<String> response = restTemplate.exchange(url,HttpMethod.GET, null, String.class);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
	}
	
}
