package org.snomed.snowstorm.core.data.services;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiErrorFactoryTest {

	@Test
	void createErrorForMergeConflictsUsesStringKeyedPlainMaps() {
		Map<Long, Long> missingSource = new Long2LongOpenHashMap();
		missingSource.put(111L, 222L);
		missingSource.put(333L, 444L);

		Map<String, ConceptMini> axioms = new HashMap<>();
		axioms.put("ax-1", new ConceptMini("100001", Collections.emptyList()));
		axioms.put("ax-2", null);

		IntegrityIssueReport report = new IntegrityIssueReport();
		report.setRelationshipsWithMissingOrInactiveSource(missingSource);
		report.setRelationshipsWithMissingOrInactiveType(Collections.emptyMap());
		report.setRelationshipsWithMissingOrInactiveDestination(null);
		report.setAxiomsWithMissingOrInactiveReferencedConcept(axioms);

		ApiError apiError = ApiErrorFactory.createErrorForMergeConflicts("Integrity check failed", report);

		assertEquals("Integrity check failed", apiError.getMessage());
		assertEquals("The integrity check API can be used here.", apiError.getDeveloperMessage());

		@SuppressWarnings("unchecked")
		Map<String, Object> integrityIssues =
				(Map<String, Object>) apiError.getAdditionalInfo().get("integrityIssues");
		assertNotNull(integrityIssues);

		@SuppressWarnings("unchecked")
		Map<String, Long> source =
				(Map<String, Long>) integrityIssues.get("relationshipsWithMissingOrInactiveSource");
		assertEquals(2, source.size());
		assertEquals(222L, source.get("111"));
		assertEquals(444L, source.get("333"));
		assertFalse(source.containsKey(111L), "Long keys must be converted to strings for ES/JSON");

		@SuppressWarnings("unchecked")
		Map<String, Long> destination =
				(Map<String, Long>) integrityIssues.get("relationshipsWithMissingOrInactiveDestination");
		assertNotNull(destination);
		assertTrue(destination.isEmpty());

		@SuppressWarnings("unchecked")
		Map<String, Long> axiomMap =
				(Map<String, Long>) integrityIssues.get("axiomsWithMissingOrInactiveReferencedConcept");
		assertEquals(100001L, axiomMap.get("ax-1"));
		assertNull(axiomMap.get("ax-2"));
	}
}
