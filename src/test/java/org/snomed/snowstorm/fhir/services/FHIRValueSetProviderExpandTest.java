package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FHIRValueSetProviderExpandTest extends AbstractFHIRTest {

	private static final String VALUE_SET_EXPANSION_POST_BODY_WITH_VERSION = "{\n" +
			"    \"resourceType\": \"Parameters\",\n" +
			"    \"parameter\": [\n" +
			"        {\n" +
			"            \"name\": \"valueSet\",\n" +
			"            \"resource\": {\n" +
			"                \"resourceType\": \"ValueSet\",\n" +
			"                \"compose\": {\n" +
			"                    \"include\": [\n" +
			"                        {\n" +
			"                            \"valueSet\": \"http://snomed.info/sct?fhir_vs=ecl/32570581000036105\" \n" +
			"                        }\n" +
			"                    ]\n" +
			"                }\n" +
			"            }\n" +
			"        },\n" +
			"        {\n" +
			"            \"name\": \"displayLanguage\",\n" +
			"            \"valueString\": \"en-GB\"\n" +
			"        },\n" +
			"        {\n" +
			"            \"name\": \"version\",\n" +
			"            \"valueString\": \"test\"\n" +
			"        },\n" +
			"        {\n" +
			"            \"name\": \"offset\",\n" +
			"            \"valueString\": \"1\"\n" +
			"        },\n" +
			"        {\n" +
			"            \"name\": \"count\",\n" +
			"            \"valueString\": \"2\"\n" +
			"        }\n" +
			"    ]\n" +
			"}";

	@Test
	public void testExpandThrowsFHIROperationExceptionIfVersionUsedWhilePerformingPOSTOperation() {
		HttpEntity<String> request = new HttpEntity<>(VALUE_SET_EXPANSION_POST_BODY_WITH_VERSION, headers);
		ResponseEntity<MethodOutcome> response = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, request, MethodOutcome.class);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

}
