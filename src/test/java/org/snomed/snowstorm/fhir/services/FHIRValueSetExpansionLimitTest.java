package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the "expansion too costly" guard in FHIRValueSetService.
 * <p>
 * The production default limit is 1000 concepts, which the FHIR test fixture cannot exceed. Overriding it via
 * {@code @TestPropertySource} would spin up a second Spring context and corrupt the Elasticsearch indices shared with
 * the other FHIR tests, so the limit is lowered on the live bean for the duration of each test and restored afterwards.
 * <p>
 * The ECL {@code <<138875005} expansion returns 13 concepts in the fixture, comfortably above the limit set here.
 */
class FHIRValueSetExpansionLimitTest extends AbstractFHIRTest {

	private static final String LIMIT_FIELD = "defaultMaxExpansionSize";
	private static final int TEST_LIMIT = 5;
	private static final int FIXTURE_EXPANSION_SIZE = 13;

	private static final String DESC_OR_SELF_ROOT =
			"/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&_format=json";

	@Autowired
	private FHIRValueSetService valueSetService;

	private int originalLimit;

	@BeforeEach
	void lowerExpansionLimit() {
		originalLimit = (int) ReflectionTestUtils.getField(valueSetService, LIMIT_FIELD);
		ReflectionTestUtils.setField(valueSetService, LIMIT_FIELD, TEST_LIMIT);
	}

	@AfterEach
	void restoreExpansionLimit() {
		ReflectionTestUtils.setField(valueSetService, LIMIT_FIELD, originalLimit);
	}

	@Test
	void expansionOverDefaultLimitIsRefusedAsTooCostly() {
		// No count supplied, so the default limit applies and 13 > 5.
		ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + DESC_OR_SELF_ROOT, String.class);

		// 422, not 404: the request is well formed, we are declining to process it.
		expectResponse(response, 422, "too-costly");
		expectResponse(response, 422, "has too many codes to produce (>" + TEST_LIMIT + ")");
	}

	@Test
	void explicitCountIsHonouredEvenAboveTheDefaultLimit() {
		// An explicit count means the client has bounded the work, so the default limit does not apply.
		ResponseEntity<String> response =
				restTemplate.getForEntity(baseUrl + DESC_OR_SELF_ROOT + "&count=" + FIXTURE_EXPANSION_SIZE, String.class);

		expectResponse(response, 200);
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, response.getBody());
		assertEquals(FIXTURE_EXPANSION_SIZE, valueSet.getExpansion().getContains().size());
	}

	@Test
	void allowMaximumSizeExpansionLiftsTheDefaultLimit() {
		// allowMaximumSizeExpansion is only read from a POST Parameters body - it is not a GET query parameter.
		HttpEntity<String> request = new HttpEntity<>("""
				{
					"resourceType": "Parameters",
					"parameter": [
						{ "name": "url", "valueUri": "http://snomed.info/sct?fhir_vs=ecl/<<%s" },
						{ "name": "allowMaximumSizeExpansion", "valueBoolean": true }
					]
				}""".formatted(Concepts.SNOMEDCT_ROOT), headers);

		ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, request, String.class);

		expectResponse(response, 200);
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, response.getBody());
		assertEquals(FIXTURE_EXPANSION_SIZE, valueSet.getExpansion().getContains().size());
	}
}
