package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class ValueSetProviderTestSCRUD {
	
	@LocalServerPort
	protected int port;
	
	@Autowired
	protected TestRestTemplate restTemplate;
	
	protected IParser fhirJsonParser;
	
	String baseUrl;
	HttpHeaders headers;
	
	@Before
	synchronized public void setup() throws ServiceException, InterruptedException {
		fhirJsonParser = FhirContext.forR4().newJsonParser();
		baseUrl = "http://localhost:" + port + "/fhir/ValueSet";
		headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "fhir+json", StandardCharsets.UTF_8));
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
	
	}
	
	@Test
	public void testValueSetCrudOperations() {
		String testURL = "http://some.test";
		ValueSet testVS = new ValueSet();
		testVS.setUrl(testURL);
		String vsJson = fhirJsonParser.encodeResourceToString(testVS);
		
		String id = storeVs(vsJson);
		
		//Now recover that ValueSet we just saved
		String url = baseUrl + "/" + id;
		ResponseEntity<String> response2 = restTemplate.getForEntity(url, String.class);
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
		assertEquals(1, bundle.getTotal());
		assertEquals(ResourceType.class, bundle.getResourceType().getClass());

		//And finally delete the vs
		restTemplate.delete(baseUrl);
		
		//And prove it's no longer found
		url = baseUrl + "/" + id;
		request = new HttpEntity<>(vsJson, headers);
		response2 = restTemplate.getForEntity(url, String.class);
		assertEquals(HttpStatus.NOT_FOUND, response2);
	
	}

	private String storeVs(String vsJson) {
		HttpEntity<String> request = new HttpEntity<>(vsJson, headers);
		ResponseEntity<MethodOutcome> response = restTemplate.postForEntity(baseUrl, request, MethodOutcome.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		MethodOutcome outcome = response.getBody();
		assertNull(outcome);
		String location = response.getHeaders().getFirst("Location");
		return getId(location);
	}

	private String getId(String location) {
		int cutFrom = location.indexOf("fhir/ValueSet/") + 14;
		int cutTo = location.indexOf('/', cutFrom);
		return location.substring(cutFrom, cutTo);
	}
	
	@Test
	public void testValueSetExample() {
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream is = classloader.getResourceAsStream("dummy-fhir-content/exampleVS.json");
		assertNotNull(is);
		ValueSet exampleVS = fhirJsonParser.parseResource(ValueSet.class, is);
		String vsJson = fhirJsonParser.encodeResourceToString(exampleVS);
		String savedId = storeVs(vsJson);
		assertNotNull(savedId);
		
		//Now recover that ValueSet we just saved
		String url = baseUrl + "/" + savedId;
		ResponseEntity<String> response2 = restTemplate.getForEntity(url, String.class);
		ValueSet savedVS = fhirJsonParser.parseResource(ValueSet.class, response2.getBody());
	}
	
}
