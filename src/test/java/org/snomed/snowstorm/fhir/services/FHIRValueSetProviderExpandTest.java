package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FHIRValueSetProviderExpandTest extends AbstractFHIRTest {

	private static final String VALUE_SET_EXPANSION_POST_BODY_WITH_VERSION = """
            {
                "resourceType": "Parameters",
                "parameter": [
                    {
                        "name": "valueSet",
                        "resource": {
                            "resourceType": "ValueSet",
                            "compose": {
                                "include": [
                                    {
                                        "valueSet": "http://snomed.info/sct?fhir_vs=ecl/32570581000036105"\s
                                    }
                                ]
                            }
                        }
                    },
                    {
                        "name": "displayLanguage",
                        "valueString": "en-GB"
                    },
                    {
                        "name": "version",
                        "valueString": "test"
                    }
                ]
            }""";

	private static final String VALUE_SET_WITH_INACTIVE_CODE = "{\n" +
			"    \"resourceType\": \"Parameters\",\n" +
			"    \"parameter\": [\n" +
			"        {\n" +
			"            \"name\": \"valueSet\",\n" +
			"            \"resource\": {\n" +
			"                \"resourceType\": \"ValueSet\",\n" +
			"                \"compose\": {\n" +
			"                    \"include\": [\n" +
			"                        {\n" +
			"                            \"system\": \"http://snomed.info/sct\",\n" +
			"                            \"concept\": [ { \"code\": \"" + sampleInactiveSCTID + "\" } ]" +
			"                        }\n" +
			"                    ]\n" +
			"                }\n" +
			"            }\n" +
			"        }\n" +
			"    ]\n" +
			"}";

	@Test
	public void testExpandThrowsFHIROperationExceptionIfVersionUsedWhilePerformingPOSTOperation() {
		HttpEntity<String> request = new HttpEntity<>(VALUE_SET_EXPANSION_POST_BODY_WITH_VERSION, headers);
		ResponseEntity<MethodOutcome> response = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, request, MethodOutcome.class);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	public void testExpandInactiveCodeViaPOST() {
		HttpEntity<String> request = new HttpEntity<>(VALUE_SET_WITH_INACTIVE_CODE, headers);
		ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, request, String.class);
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, response.getBody());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		ValueSet.ValueSetExpansionContainsComponent concept = valueSet.getExpansion().getContains().get(0);
		assertEquals("http://snomed.info/sct", concept.getSystem());
		assertEquals("60363000", concept.getCode());
		assertTrue(concept.getInactive());
	}

}
