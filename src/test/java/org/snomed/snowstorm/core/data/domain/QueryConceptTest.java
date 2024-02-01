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

import static org.junit.jupiter.api.Assertions.assertEquals;

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

		queryConcept.addAttribute(3, 234L, 0.0005f);
		assertEquals("1:123=456,789:1234=123|3:123=456:234=#0.0005", queryConcept.getAttrMap());

		queryConcept.addAttribute(3, 234L, 500.0f);
		assertEquals("1:123=456,789:1234=123|3:123=456:234=#0.0005,#500.0", queryConcept.getAttrMap());

		queryConcept.addAttribute(4, 2345L, "\"test\"");
		assertEquals("1:123=456,789:1234=123|3:123=456:234=#0.0005,#500.0|4:2345=\"test\"", queryConcept.getAttrMap());

		queryConcept.serializeGroupedAttributesMap();
		Map<Integer, Map<String, List<Object>>> groupedAttributesMap = queryConcept.getGroupedAttributesMap();
		assertEquals(3, groupedAttributesMap.size());

		Map<String, Set<Object>> expectedAttrMap = new HashMap<>();
		expectedAttrMap.put("all", Sets.newHashSet("123", "456", "789", "\"test\""));
		expectedAttrMap.put("123", Sets.newHashSet("456", "789"));
		expectedAttrMap.put("234", Sets.newHashSet(500.0f, 0.0005f));
		expectedAttrMap.put("2345", Sets.newHashSet("\"test\""));
		expectedAttrMap.put("1234", Sets.newHashSet("123"));
		expectedAttrMap.put("all_numeric", Sets.newHashSet(500.0f, 0.0005f));
		assertEquals(expectedAttrMap, queryConcept.getAttr());

		String json = objectMapper.writeValueAsString(queryConcept);

		QueryConcept queryConcept2 = objectMapper.readValue(json, QueryConcept.class);
		assertEquals("1:123=456,789:1234=123|3:123=456:234=#0.0005,#500.0|4:2345=\"test\"", queryConcept2.getAttrMap());
		assertEquals(groupedAttributesMap, queryConcept2.getGroupedAttributesMap());
	}

	@Test
	void testDeserialize() {
		String attrMapString = "0:411116001=139011000036106:1142140007=#2:999000001000168109=\"CBD 27:1 THC\":774158006=1483921000168102|" +
				"1:999000031000168102=258798001:999000021000168100=#53.5:732943007=96223000:127489000=96223000" +
				"|2:999000031000168102=258798001:999000021000168100=#0.3:732943007=96225007:127489000=96225007";

		QueryConcept queryConcept = new QueryConcept();
		queryConcept.setAttrMap(attrMapString);
		Map<Integer, Map<String, List<Object>>> result = queryConcept.getGroupedAttributesMap();
		assertEquals(3, result.keySet().size());
		assertEquals(4, result.get(0).size());
	}

}
