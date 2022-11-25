package org.snomed.snowstorm.fhir.services;


import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemProviderInstancesTest extends AbstractFHIRTest {

	@Test
	void testCodeSystemRecovery() {
		String url = baseUrl + "/CodeSystem";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 200);
		Bundle bundle = fhirJsonParser.parseResource(Bundle.class, response.getBody());
		assertNotNull(bundle.getEntry());
		assertEquals(3, bundle.getEntry().size());
		for (BundleEntryComponent entry : bundle.getEntry()) {
			CodeSystem cs = (CodeSystem) entry.getResource();
			assertTrue(cs.getTitle().contains("SNOMED CT") || cs.getTitle().contains("ICD-10"));
		}
	}
	
	@Test
	void testCodeSystemRecoverySorted() {
		String url = baseUrl + "/CodeSystem?_sort=title,-date";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 200);
		Bundle bundle = fhirJsonParser.parseResource(Bundle.class, response.getBody());
		assertNotNull(bundle.getEntry());
		assertEquals(3, bundle.getEntry().size());
		for (BundleEntryComponent entry : bundle.getEntry()) {
			CodeSystem cs = (CodeSystem)(entry.getResource());
			String title = cs.getTitle();
			assertTrue(title.contains("SNOMED CT") || title.contains("ICD-10"));
		}
	}
	
	@Test
	void testCodeSystemRecoverySortedExpectedFail() {
		String url = baseUrl + "/CodeSystem?_sort=foo,-bar";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 400);
	}
	
}
