package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiErrorFactory {

	static final String INTEGRITY_CHECK_DEVELOPER_MESSAGE = "The integrity check API can be used here.";

	static ApiError createErrorForMergeConflicts(String message) {
		return new ApiError(message, INTEGRITY_CHECK_DEVELOPER_MESSAGE);
	}

	static ApiError createErrorForMergeConflicts(String message, IntegrityIssueReport integrityIssueReport) {
		// Use only plain Maps (no custom types) so ApiError JSON round-trips cleanly for ES storage.
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("relationshipsWithMissingOrInactiveDestination",
				toStringKeyedMap(integrityIssueReport.getRelationshipsWithMissingOrInactiveDestination()));
		report.put("relationshipsWithMissingOrInactiveSource",
				toStringKeyedMap(integrityIssueReport.getRelationshipsWithMissingOrInactiveSource()));
		report.put("relationshipsWithMissingOrInactiveType",
				toStringKeyedMap(integrityIssueReport.getRelationshipsWithMissingOrInactiveType()));
		report.put("axiomsWithMissingOrInactiveReferencedConcept",
				toAxiomConceptIdMap(integrityIssueReport.getAxiomsWithMissingOrInactiveReferencedConcept()));

		Map<String, Object> info = new HashMap<>();
		info.put("integrityIssues", report);
		return new ApiError(message, INTEGRITY_CHECK_DEVELOPER_MESSAGE, info);
	}

	/**
	 * Elasticsearch/Jackson cannot reliably persist maps with Long keys (including fastutil Long2LongOpenHashMap).
	 * JSON object keys are strings anyway, so convert before storing on BranchMergeJob.
	 */
	private static Map<String, Long> toStringKeyedMap(Map<Long, Long> map) {
		if (map == null || map.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, Long> result = new LinkedHashMap<>();
		for (Map.Entry<Long, Long> entry : map.entrySet()) {
			if (entry.getKey() != null) {
				result.put(entry.getKey().toString(), entry.getValue());
			}
		}
		return result;
	}

	private static Map<String, Long> toAxiomConceptIdMap(Map<String, ConceptMini> axioms) {
		if (axioms == null || axioms.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, Long> result = new LinkedHashMap<>();
		for (Map.Entry<String, ConceptMini> entry : axioms.entrySet()) {
			ConceptMini conceptMini = entry.getValue();
			result.put(entry.getKey(), conceptMini != null ? conceptMini.getConceptIdAsLong() : null);
		}
		return result;
	}
}
