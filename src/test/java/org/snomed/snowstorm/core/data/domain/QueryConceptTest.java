package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

class QueryConceptTest {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setup() {
		objectMapper = Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.build();
	}

	@Test
	void test() throws IOException {
		QueryConcept queryConcept = new QueryConcept();
		queryConcept.setConceptIdL(123L);
		queryConcept.setPath("MAIN");

		queryConcept.addAttribute(1, 123L, "456");
		assertEquals("1:123=456", queryConcept.getAttrMap());

		queryConcept.addAttribute(1, 123L, "789");
		assertEquals("1:123=456,789", queryConcept.getAttrMap());

		queryConcept.addAttribute(1, 1234L, "123");
		assertEquals("1:123=456,789:1234=123", queryConcept.getAttrMap());

		queryConcept.addAttribute(3, 123L, "456");
		assertEquals("1:123=456,789:1234=123|3:123=456", queryConcept.getAttrMap());

		Map<Integer, Map<String, List<String>>> groupedAttributesMap = queryConcept.getGroupedAttributesMap();
		assertEquals(2, groupedAttributesMap.size());

		Map<String, Set<String>> expectedAttrMap = new HashMap<>();
		expectedAttrMap.put("all", Sets.newHashSet("123", "456", "789"));
		expectedAttrMap.put("123", Sets.newHashSet("456", "789"));
		expectedAttrMap.put("1234", Sets.newHashSet("123"));
		assertEquals(expectedAttrMap, queryConcept.getAttr());

		String json = objectMapper.writeValueAsString(queryConcept);

		QueryConcept queryConcept2 = objectMapper.readValue(json, QueryConcept.class);
		assertEquals("1:123=456,789:1234=123|3:123=456", queryConcept2.getAttrMap());
		assertEquals(groupedAttributesMap, queryConcept2.getGroupedAttributesMap());
	}

}
