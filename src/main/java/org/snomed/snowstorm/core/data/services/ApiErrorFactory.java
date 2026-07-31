package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ApiErrorFactory {

	static ApiError createErrorForMergeConflicts(String message, IntegrityIssueReport integrityIssueReport) {
		PersistedIntegrityIssueReport report = new PersistedIntegrityIssueReport();
		report.setRelationshipsWithMissingOrInactiveDestination(
				toStringKeyedMap(integrityIssueReport.getRelationshipsWithMissingOrInactiveDestination()));
		report.setRelationshipsWithMissingOrInactiveSource(
				toStringKeyedMap(integrityIssueReport.getRelationshipsWithMissingOrInactiveSource()));
		report.setRelationshipsWithMissingOrInactiveType(
				toStringKeyedMap(integrityIssueReport.getRelationshipsWithMissingOrInactiveType()));
		if (integrityIssueReport.getAxiomsWithMissingOrInactiveReferencedConcept() != null) {
			Map<String, Long> axiomsWithMissingOrInactiveReferencedConcept = new HashMap<>();
			for (Map.Entry<String, ConceptMini> entry : integrityIssueReport.getAxiomsWithMissingOrInactiveReferencedConcept().entrySet()) {
				ConceptMini conceptMini = entry.getValue();
				axiomsWithMissingOrInactiveReferencedConcept.put(entry.getKey(),
						conceptMini != null ? conceptMini.getConceptIdAsLong() : null);
			}
			report.setAxiomsWithMissingOrInactiveReferencedConcept(axiomsWithMissingOrInactiveReferencedConcept);
		} else {
			report.setAxiomsWithMissingOrInactiveReferencedConcept(null);
		}

		Map<String, Object> info = new HashMap<>();
		info.put("integrityIssues", report);
		return new ApiError(message, "The integrity check API can be used here.", info);
	}

	/**
	 * Elasticsearch/Jackson cannot reliably persist maps with Long keys (including fastutil Long2LongOpenHashMap).
	 * JSON object keys are strings anyway, so convert before storing on BranchMergeJob.
	 */
	private static Map<String, Long> toStringKeyedMap(Map<Long, Long> map) {
		if (map == null) {
			return Collections.emptyMap();
		}
		Map<String, Long> result = new HashMap<>();
		for (Map.Entry<Long, Long> entry : map.entrySet()) {
			if (entry.getKey() != null) {
				result.put(entry.getKey().toString(), entry.getValue());
			}
		}
		return result;
	}

	private static class PersistedIntegrityIssueReport {

		private Map<String, Long> axiomsWithMissingOrInactiveReferencedConcept;
		private Map<String, Long> relationshipsWithMissingOrInactiveSource;
		private Map<String, Long> relationshipsWithMissingOrInactiveType;
		private Map<String, Long> relationshipsWithMissingOrInactiveDestination;


		public Map<String, Long> getAxiomsWithMissingOrInactiveReferencedConcept() {
			return axiomsWithMissingOrInactiveReferencedConcept;
		}

		public void setAxiomsWithMissingOrInactiveReferencedConcept(Map<String, Long> axiomsWithMissingOrInactiveReferencedConcept) {
			this.axiomsWithMissingOrInactiveReferencedConcept = axiomsWithMissingOrInactiveReferencedConcept;
		}

		public Map<String, Long> getRelationshipsWithMissingOrInactiveSource() {
			return relationshipsWithMissingOrInactiveSource;
		}

		public void setRelationshipsWithMissingOrInactiveSource(Map<String, Long> relationshipsWithMissingOrInactiveSource) {
			this.relationshipsWithMissingOrInactiveSource = relationshipsWithMissingOrInactiveSource;
		}

		public Map<String, Long> getRelationshipsWithMissingOrInactiveType() {
			return relationshipsWithMissingOrInactiveType;
		}

		public void setRelationshipsWithMissingOrInactiveType(Map<String, Long> relationshipsWithMissingOrInactiveType) {
			this.relationshipsWithMissingOrInactiveType = relationshipsWithMissingOrInactiveType;
		}

		public Map<String, Long> getRelationshipsWithMissingOrInactiveDestination() {
			return relationshipsWithMissingOrInactiveDestination;
		}

		public void setRelationshipsWithMissingOrInactiveDestination(Map<String, Long> relationshipsWithMissingOrInactiveDestination) {
			this.relationshipsWithMissingOrInactiveDestination = relationshipsWithMissingOrInactiveDestination;
		}
	}
}
