package org.snomed.snowstorm.fhir.services;


import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeSystem;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.text.SimpleDateFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemProviderInstancesTest extends AbstractFHIRTest {

	@Test
	void testCodeSystemRecovery() {
		String url = baseUrl + "/CodeSystem";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 200);
		Bundle bundle = fhirJsonParser.parseResource(Bundle.class, response.getBody());
		assertNotNull(bundle.getEntry());
		//This line seems a bit variable as to whether we're getting 3 or 4.
		//Think we've got some other test that is not cleaning up properly.
		assertTrue(bundle.getEntry().size() >=3, () -> {
			StringBuilder buffer = new StringBuilder();
			for (BundleEntryComponent component : bundle.getEntry()) {
				buffer.append(component.getFullUrl())
						.append(" ");
			}
			return buffer.toString();
		});

		for (BundleEntryComponent entry : bundle.getEntry()) {
			CodeSystem cs = (CodeSystem) entry.getResource();
			assertTrue(cs.getTitle().contains("SNOMED CT") || cs.getTitle().contains("ICD-10"), () -> "Found title " + cs.getTitle());
		}
	}
	
	@Test
	void testCodeSystemRecoverySorted() {
		assertEquals("[" +
						"ICD-10|null, " +
						"SNOMED CT release 2019-01-31|20190131, " +
						"SNOMED CT release 2019-07-31|20190731" +
				"]", getCodeSystemsTitleDate(baseUrl + "/CodeSystem?_sort=title,-date").toString());

		assertEquals("[" +
				"SNOMED CT release 2019-07-31|20190731, " +
				"SNOMED CT release 2019-01-31|20190131, " +
				"ICD-10|null" +
				"]", getCodeSystemsTitleDate(baseUrl + "/CodeSystem?_sort=-title,date").toString());
	}

	private @NotNull List<String> getCodeSystemsTitleDate(String url) {
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 200);
		Bundle bundle = fhirJsonParser.parseResource(Bundle.class, response.getBody());
		assertNotNull(bundle.getEntry());
		assertEquals(3, bundle.getEntry().size());
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		return bundle.getEntry().stream().map(entry -> {
			CodeSystem codeSystem = (CodeSystem) (entry.getResource());
			return "%s|%s".formatted(codeSystem.getTitle(), codeSystem.getDate() != null ? dateFormat.format(codeSystem.getDate()) : "null");
		}).toList();
	}

	@Test
	void testCodeSystemRecoverySortedExpectedFail() {
		String url = baseUrl + "/CodeSystem?_sort=foo,-bar";
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 400);
	}
	
}
