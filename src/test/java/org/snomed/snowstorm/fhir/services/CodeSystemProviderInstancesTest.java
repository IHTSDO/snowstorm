package org.snomed.snowstorm.fhir.services;


import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class CodeSystemProviderInstancesTest extends AbstractFHIRTest {

	@Test
	void testCodeSystemRecovery() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 200);
		Bundle bundle = fhirJsonParser.parseResource(Bundle.class, response.getBody());
		assertNotNull(bundle.getEntry());
		assertEquals(2, bundle.getEntry().size());
		for (BundleEntryComponent entry : bundle.getEntry()) {
			CodeSystem cs = (CodeSystem)(entry.getResource());
			assertTrue(cs.getTitle().contains("SNOMED CT"));
		}
	}
	
	@Test
	void testCodeSystemRecoverySorted() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem?_sort=title,-date";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 200);
		Bundle bundle = fhirJsonParser.parseResource(Bundle.class, response.getBody());
		assertNotNull(bundle.getEntry());
		assertEquals(2, bundle.getEntry().size());
		for (BundleEntryComponent entry : bundle.getEntry()) {
			CodeSystem cs = (CodeSystem)(entry.getResource());
			assertTrue(cs.getTitle().contains("SNOMED CT"));
		}
	}
	
	@Test
	void testCodeSystemRecoverySortedExpectedFail() {
		String url = "http://localhost:" + port + "/fhir/CodeSystem?_sort=foo,-bar";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 400);
	}
	
}
