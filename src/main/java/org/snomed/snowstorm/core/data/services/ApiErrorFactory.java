package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;

import java.util.HashMap;
import java.util.Map;

public class ApiErrorFactory {

	static ApiError createErrorForMergeConflicts(String message, IntegrityIssueReport integrityIssueReport) {
		PersistedIntegrityIssueReport report = new PersistedIntegrityIssueReport();
		report.setRelationshipsWithMissingOrInactiveDestination(integrityIssueReport.getRelationshipsWithMissingOrInactiveDestination());
		report.setRelationshipsWithMissingOrInactiveSource(integrityIssueReport.getRelationshipsWithMissingOrInactiveSource());
		report.setRelationshipsWithMissingOrInactiveType(integrityIssueReport.getRelationshipsWithMissingOrInactiveType());
		if (integrityIssueReport.getAxiomsWithMissingOrInactiveReferencedConcept() != null) {
			Map<String, Long> axiomsWithMissingOrInactiveReferencedConcept = new HashMap<>();
			for (Map.Entry<String, ConceptMini> entry : integrityIssueReport.getAxiomsWithMissingOrInactiveReferencedConcept().entrySet()) {
				axiomsWithMissingOrInactiveReferencedConcept.put(entry.getKey(), entry.getValue().getConceptIdAsLong());
			}
			report.setAxiomsWithMissingOrInactiveReferencedConcept(axiomsWithMissingOrInactiveReferencedConcept);
		} else {
			report.setAxiomsWithMissingOrInactiveReferencedConcept(null);
		}

		Map <String, Object> info = new HashMap <>();
		info.put("integrityIssues", report);
		return new ApiError(message, "The integrity check API can be used here.", info);
	}

	private static class PersistedIntegrityIssueReport {

		private Map<String, Long> axiomsWithMissingOrInactiveReferencedConcept;
		private Map<Long, Long> relationshipsWithMissingOrInactiveSource;
		private Map<Long, Long> relationshipsWithMissingOrInactiveType;
		private Map<Long, Long> relationshipsWithMissingOrInactiveDestination;


		public Map<String, Long> getAxiomsWithMissingOrInactiveReferencedConcept() {
			return axiomsWithMissingOrInactiveReferencedConcept;
		}

		public void setAxiomsWithMissingOrInactiveReferencedConcept(Map<String, Long> axiomsWithMissingOrInactiveReferencedConcept) {
			this.axiomsWithMissingOrInactiveReferencedConcept = axiomsWithMissingOrInactiveReferencedConcept;
		}

		public Map<Long, Long> getRelationshipsWithMissingOrInactiveSource() {
			return relationshipsWithMissingOrInactiveSource;
		}

		public void setRelationshipsWithMissingOrInactiveSource(Map<Long, Long> relationshipsWithMissingOrInactiveSource) {
			this.relationshipsWithMissingOrInactiveSource = relationshipsWithMissingOrInactiveSource;
		}

		public Map<Long, Long> getRelationshipsWithMissingOrInactiveType() {
			return relationshipsWithMissingOrInactiveType;
		}

		public void setRelationshipsWithMissingOrInactiveType(Map<Long, Long> relationshipsWithMissingOrInactiveType) {
			this.relationshipsWithMissingOrInactiveType = relationshipsWithMissingOrInactiveType;
		}

		public Map<Long, Long> getRelationshipsWithMissingOrInactiveDestination() {
			return relationshipsWithMissingOrInactiveDestination;
		}

		public void setRelationshipsWithMissingOrInactiveDestination(Map<Long, Long> relationshipsWithMissingOrInactiveDestination) {
			this.relationshipsWithMissingOrInactiveDestination = relationshipsWithMissingOrInactiveDestination;
		}
	}
}
