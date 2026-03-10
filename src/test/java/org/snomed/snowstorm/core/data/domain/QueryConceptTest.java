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

import static org.junit.jupiter.api.Assertions.*;

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

	@Test
	void testDeserializeUrlValueWithoutQuotesFails() {
		String attrMapString = "0:62191000087109=26643006:411116001=http://monographs.termspace.com/en_ca/EDARBI_02381389_BAUSCH HEALTH";
		QueryConcept queryConcept = new QueryConcept();
		queryConcept.setAttrMap(attrMapString);
		assertThrows(IllegalArgumentException.class, queryConcept::getGroupedAttributesMap);
	}

    @Test
    void testDeserializeUrlValueWithQuotes() {
        String attrMapString = """
            0:62191000087109=26643006:411116001=421026006:62171000087105="http://monographs.termspace.com/en_ca/EDARBI_02381389_BAUSCH HEALTH, CANADA INC._MARKETED_EN_2023-11-09.pdf"
            :774159003=90441000087109:763032000=732936001:774158006=80341000087102:62581000087102=62551000087105:1142139005=#1|1:732947008=732936001:762949000=895431005:732943007=449561004:732945000=258684004:1142135004=#40.0:1142136003=#1.0
            """;
            QueryConcept queryConcept = new QueryConcept();
        queryConcept.setAttrMap(attrMapString);

        assertDoesNotThrow(queryConcept::getGroupedAttributesMap);
    }

	/**
	 * Verifies the fix for "attrMap is being modified after loading": when a document has a correctly
	 * quoted attrMap (value contains a comma inside quotes), the copy constructor calls
	 * serializeGroupedAttributesMap() which deserializes then re-serializes. Without the fix, the
	 * deserializer split on comma ignoring quotes, producing two values that after sort produced the
	 * malformed string and an IllegalArgumentException. With the fix, the comma inside quotes is not
	 * split, so the value remains one and no error occurs.
	 */
	@Test
	void testCopyConstructorDoesNotCorruptAttrMapWhenValueContainsCommaInsideQuotes() {
		// Stored document has correctly quoted value (comma inside the quoted string)
		String quotedAttrMap = "0:62191000087109=26643006:411116001=421026006:774159003=90441000087109" +
				":62171000087105=\"http://monographs.termspace.com/en_ca/EDARBI_02381389_BAUSCH HEALTH, CANADA INC._MARKETED_EN_2023-11-09.pdf\"" +
				":763032000=732936001:774158006=80341000087102:62581000087102=62551000087105:1142139005=#1" +
				"|1:732947008=732936001:762949000=895431005:732943007=449561004:732945000=258684004:1142135004=#40.0:1142136003=#1.0";

		QueryConcept loaded = new QueryConcept();
		loaded.setConceptIdL(230091000087105L);
		loaded.setPath("MAIN");
		loaded.setAttrMap(quotedAttrMap);
		loaded.setParents(Set.of(26643006L));
		loaded.setAncestors(Set.of(26643006L, 421026006L));
		loaded.setStated(false);

		// Copy constructor runs serializeGroupedAttributesMap() which deserializes then re-serializes.
		// Without fix: comma inside quotes was split → two values → sort → malformed attrMap → fail on next deserialize or in fieldsMatch.
		QueryConcept copy = assertDoesNotThrow(() -> new QueryConcept(loaded),
				"Copy constructor must not throw when attrMap has comma inside quoted value");

		// Attribute 62171000087105 must still be a single value containing the comma (not split into two)
		Map<Integer, Map<String, List<Object>>> grouped = copy.getGroupedAttributesMap();
		assertNotNull(grouped);
		assertTrue(grouped.containsKey(0), "Group 0 should exist");
		List<Object> values = grouped.get(0).get("62171000087105");
		assertNotNull(values, "Attribute 62171000087105 should exist");
		assertEquals(1, values.size(), "Value should remain single (comma inside quotes not split); was: " + values);
		String singleValue = (String) values.get(0);
		assertTrue(singleValue.contains("http://monographs.termspace.com"), "Value should contain URL");
		assertTrue(singleValue.contains("CANADA INC._MARKETED_EN_2023-11-09.pdf"), "Value should contain filename");
		assertTrue(singleValue.contains(","), "Value should contain the comma (one value with comma inside)");

		// fieldsMatch also calls getGroupedAttributesMap() on both; must not throw
		assertTrue(loaded.fieldsMatch(copy), "Loaded and copy should match after round-trip");
	}
}
