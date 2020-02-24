package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.kaicode.elasticvc.api.BranchService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class ValueSetProviderScrudTest {
	
	@LocalServerPort
	protected int port;
	
	@Autowired
	protected TestRestTemplate restTemplate;
	
	@Autowired
	protected BranchService branchService;
	
	protected IParser fhirJsonParser;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	String baseUrl;
	HttpHeaders headers;
	
	@Before
	synchronized public void setup() throws ServiceException, InterruptedException {
		
		branchService.create("MAIN");
		
		fhirJsonParser = FhirContext.forR4().newJsonParser();
		baseUrl = "http://localhost:" + port + "/fhir/ValueSet";
		headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "fhir+json", StandardCharsets.UTF_8));
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
	}
	
	@After
	synchronized public void tearDown() throws ServiceException, InterruptedException {
		branchService.deleteAll();
	}
	
	@Test
	public void testValueSetCrudOperations() throws Exception {
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
		} catch (DataFormatException e) {}
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
	public void testValueSetExpansion() {
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream is = classloader.getResourceAsStream("dummy-fhir-content/exampleVS_ECL.json");
		assertNotNull(is);
		ValueSet exampleVS = fhirJsonParser.parseResource(ValueSet.class, is);
		String vsJson = fhirJsonParser.encodeResourceToString(exampleVS);
		storeVs("chronic-diseases", vsJson);
		
		//Now expand that ValueSet we just saved
		String url = baseUrl + "/chronic-diseases/$expand";
		ResponseEntity<String> response2 = restTemplate.getForEntity(url, String.class);
		ValueSet savedVS = fhirJsonParser.parseResource(ValueSet.class, response2.getBody());
		assertEquals(0, savedVS.getExpansion().getTotal());
		restTemplate.delete(baseUrl + "/chronic-diseases");
		logger.info("ValueSet expansion tested OK!");
	}

	private void storeVs(String id, String vsJson) {
		HttpEntity<String> request = new HttpEntity<>(vsJson, headers);
		ResponseEntity<MethodOutcome> response = restTemplate.exchange(baseUrl + "/" + id, HttpMethod.PUT, request, MethodOutcome.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		MethodOutcome outcome = response.getBody();
		assertNull(outcome);
	}

	@Test
	public void testValueSetExample() {
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
	public void testValueSetSearchWithCode() {
		//We do not allow expanding all ValueSets to search for a concept - too costly
		String url = baseUrl + "?code=foo";
		ResponseEntity<String> response = restTemplate.exchange(url,HttpMethod.GET, null, String.class);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
	}
	
}
