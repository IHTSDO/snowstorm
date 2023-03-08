package org.snomed.snowstorm.fhir.services;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemProviderExpressionSupplementTest extends AbstractFHIRTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@Test
	void testCreateDeleteExpressionRepo() {
		int initialCount = codeSystemService.findAll().size();

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/fhir+json");
		String version = "http://snomed.info/xsct/22000003104";
		ResponseEntity<String> response = this.restTemplate.exchange(new RequestEntity<>("{\n" +
				"  \"resourceType\" : \"CodeSystem\",\n" +
				"  \"url\" : \"http://snomed.info/sct\",\n" +
				"  \"version\" : \"" + version + "\",\n" +
				"  \"content\" : \"supplement\",\n" +
				"  \"supplements\" : \"http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20190131\"\n" +
				"}\n", headers, HttpMethod.POST, URI.create(baseUrl + "/CodeSystem")), String.class);;
		expectResponse(response, 201);

		HttpHeaders responseHeaders = response.getHeaders();
		assertNotNull(responseHeaders.getLocation());
		String location = responseHeaders.getLocation().toString();
		location = location.substring(0, location.indexOf("/_history"));
		assertTrue(location.contains("/CodeSystem/"));

		assertEquals(initialCount + 1, codeSystemService.findAll().size());

		this.restTemplate.delete(baseUrl + "/CodeSystem?url=http://snomed.info/sct&version=" + version);

		assertEquals(initialCount, codeSystemService.findAll().size());
	}
	
}
