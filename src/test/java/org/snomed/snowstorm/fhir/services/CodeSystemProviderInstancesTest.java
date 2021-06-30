package org.snomed.snowstorm.fhir.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class CodeSystemProviderInstancesTest extends AbstractFHIRTest {

	@Test
	void testCodeSystemRecovery() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		checkForError(response);
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
		checkForError(response);
		Bundle bundle = fhirJsonParser.parseResource(Bundle.class, response.getBody());
		assertNotNull(bundle.getEntry());
		assertEquals(2, bundle.getEntry().size());
		for (BundleEntryComponent entry : bundle.getEntry()) {
			CodeSystem cs = (CodeSystem)(entry.getResource());
			assertTrue(cs.getTitle().contains("SNOMED CT"));
		}
	}
	
	@Test
	void testCodeSystemRecoverySortedExpectedFail() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/CodeSystem?_sort=foo,-bar";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		checkForExpectedError(response);
	}
	
}
