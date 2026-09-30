package org.snomed.snowstorm.rest.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.snowstorm.core.util.ElasticsearchErrorTestData.*;

class RestControllerAdviceTest {

	private final RestControllerAdvice restControllerAdvice = new RestControllerAdvice();

	@Test
	void returnsElasticsearchStatusAndRootCauseReason() {
		ResponseEntity<Map<String, Object>> response = restControllerAdvice.handleElasticsearchException(searchPhaseFailure());

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(ROOT_CAUSE_REASON, response.getBody().get("message"));
	}

	@Test
	void fallsBackToExceptionMessageWithoutRootCause() {
		ResponseEntity<Map<String, Object>> response = restControllerAdvice.handleElasticsearchException(failureWithoutRootCause());

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals("[es/search] failed: [exception] something went wrong", response.getBody().get("message"));
	}
}
