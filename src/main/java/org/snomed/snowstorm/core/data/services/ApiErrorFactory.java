package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;

import java.util.HashMap;
import java.util.Map;

public class ApiErrorFactory {

	static ApiError createErrorForMergeConflicts(String message, IntegrityIssueReport integrityIssueReport) {
		Map<String, Object> info = new HashMap<>();
		info.put("integrityIssues", integrityIssueReport);
		return new ApiError(message, "The integrity check API can be used here.", info);
	}
}
