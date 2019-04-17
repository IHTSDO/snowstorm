package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;

public class IntegrityException extends ServiceException {

	private IntegrityIssueReport integrityIssueReport;

	public IntegrityException(String message, IntegrityIssueReport integrityIssueReport) {
		super(message);
		this.integrityIssueReport = integrityIssueReport;
	}

	public IntegrityIssueReport getIntegrityIssueReport() {
		return integrityIssueReport;
	}
}
