package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.dstu3.model.OperationOutcome.IssueType;

public class FHIROperationException extends Exception {

	private static final long serialVersionUID = 1L;
	
	IssueType issueType;
	
	FHIROperationException (IssueType issueType, String message) {
		super(message);
		this.issueType = issueType;
	}
	
	FHIROperationException (IssueType issueType, String message, Exception e) {
		super(message, e);
		this.issueType = issueType;
	}
	
	public IssueType getIssueType() {
		return issueType;
	}
}
