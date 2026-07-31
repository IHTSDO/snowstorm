package org.snomed.snowstorm.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElasticsearchConfigBranchMergeMappingTest {

	@Test
	void detectsLegacyNestedApiErrorObjectMapping() {
		Map<String, Object> mapping = Map.of(
				"properties", Map.of(
						"apiError", Map.of(
								"properties", Map.of(
										"message", Map.of("type", "text"),
										"additionalInfo", Map.of("type", "object")
								)
						),
						"apiErrorJson", Map.of("type", "text", "index", false)
				)
		);
		assertTrue(ElasticsearchConfig.hasNestedApiErrorObjectMapping(mapping));
	}

	@Test
	void acceptsApiErrorJsonOnlyMapping() {
		Map<String, Object> mapping = Map.of(
				"properties", Map.of(
						"apiErrorJson", Map.of("type", "text", "index", false),
						"message", Map.of("type", "text")
				)
		);
		assertFalse(ElasticsearchConfig.hasNestedApiErrorObjectMapping(mapping));
	}

	@Test
	void handlesNullOrEmptyMapping() {
		assertFalse(ElasticsearchConfig.hasNestedApiErrorObjectMapping(null));
		assertFalse(ElasticsearchConfig.hasNestedApiErrorObjectMapping(Map.of()));
	}
}
