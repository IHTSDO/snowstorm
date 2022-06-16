package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.parser.DataFormatException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.repositories.FHIRValueSetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FHIRValueSetProviderScrudTest extends AbstractFHIRTest {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private FHIRValueSetRepository valueSetRepository;

	@Test
	void testValueSetCrudOperations() throws Exception {
		valueSetRepository.deleteAll();

		String testURL = "http://some.test";
		String testId = "test";
		ValueSet testVS = new ValueSet();
		testVS.setUrl(testURL);
		testVS.setId(testId);
		testVS.setVersion("1");
		String vsJson = fhirJsonParser.encodeResourceToString(testVS);
		storeVs(testId, vsJson);

		String vsUrl = null;
		try {

			//Now recover that ValueSet we just saved
			vsUrl = baseUrl + "/ValueSet/" + testId;
			ResponseEntity<String> response2 = restTemplate.getForEntity(vsUrl, String.class);
			//Did we get an operation failed here?
			try {
				OperationOutcome oo = fhirJsonParser.parseResource(OperationOutcome.class, response2.getBody());
				throw new Exception("Unexpected outcome: " + oo.toString());
			} catch (DataFormatException e) {
				// pass
			}
			ValueSet savedVS = fhirJsonParser.parseResource(ValueSet.class, response2.getBody());

			assertEquals("1", savedVS.getVersion());
			//Also check that attempting to recover a nonsense id gives us an HTTP 404 Not Found
			response2 = restTemplate.getForEntity(baseUrl + "/ValueSet/foo", String.class);
			assertEquals(HttpStatus.NOT_FOUND, response2.getStatusCode());

			//Next update the VS
			savedVS.setUrl("http://some.other.test");
			vsJson = fhirJsonParser.encodeResourceToString(savedVS);
			HttpEntity<String> request = new HttpEntity<>(vsJson, headers);
			response2 = restTemplate.exchange(vsUrl, HttpMethod.PUT, request, String.class);
			assertEquals(HttpStatus.OK, response2.getStatusCode());
			//ValueSet updated = fhirJsonParser.parseResource(ValueSet.class, response.getBody());

			//Now recover all valuesets
			response2 = restTemplate.getForEntity(baseUrl + "/ValueSet", String.class);
			assertEquals(HttpStatus.OK, response2.getStatusCode());
			assertNotNull(response2.getBody());
			String body2 = response2.getBody();
			Bundle bundle = fhirJsonParser.parseResource(Bundle.class, body2);
			assertEquals(1, bundle.getTotal(), () -> "Found " + bundle.getTotal() + " valuesets when expected 1. Body: " + body2);
			assertEquals(ResourceType.class, bundle.getResourceType().getClass());

		} finally {
			deleteVs(testId);
		}
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

		try {
			//Now expand that ValueSet we just saved
			String url = baseUrl + "/ValueSet/chronic-diseases/$expand";
			ValueSet savedVS = getValueSet(url);
			assertEquals(0, savedVS.getExpansion().getTotal());

			restTemplate.delete(baseUrl + "/ValueSet/chronic-diseases");
			logger.info("ValueSet expansion tested OK!");
		} finally {
			deleteVs("chronic-diseases");
		}
	}

	private ValueSet getValueSet(String url) throws FHIROperationException {
		ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
		expectResponse(response, 200);
		return fhirJsonParser.parseResource(ValueSet.class, response.getBody());
	}

	@Test
	void testValueSetExample() {
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream is = classloader.getResourceAsStream("dummy-fhir-content/exampleVS.json");
		assertNotNull(is);
		ValueSet exampleVS = fhirJsonParser.parseResource(ValueSet.class, is);
		String vsJson = fhirJsonParser.encodeResourceToString(exampleVS);
		storeVs("address-use", vsJson);

		try {
			//Now recover that ValueSet we just saved
			String url = baseUrl + "/ValueSet/address-use";
			ResponseEntity<String> response2 = restTemplate.getForEntity(url, String.class);
			ValueSet savedVS = fhirJsonParser.parseResource(ValueSet.class, response2.getBody());
			restTemplate.delete(url);
			assertNotNull(savedVS);
		} finally {
			deleteVs("address-use");
		}
	}
	
	@Test
	void testValueSetSearchWithCode() {
		//We do not allow expanding all ValueSets to search for a concept - too costly
		String url = baseUrl + "/ValueSet?code=foo";
		ResponseEntity<String> response = restTemplate.exchange(url,HttpMethod.GET, null, String.class);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
	}
	
}
