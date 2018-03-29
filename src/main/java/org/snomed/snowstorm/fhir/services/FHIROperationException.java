package org.snomed.snowstorm.fhir.services;

import org.snomed.snowstorm.fhir.domain.element.FHIRIssue;

public class FHIROperationException extends Exception {

	private static final long serialVersionUID = 1L;
	
	FHIRIssue.IssueType issueType;
	
	FHIROperationException (FHIRIssue.IssueType issueType, String message) {
		super(message);
		this.issueType = issueType;
	}
	
	FHIROperationException (FHIRIssue.IssueType issueType, String message, Exception e) {
		super(message, e);
		this.issueType = issueType;
	}
	
	public FHIRIssue.IssueType getIssueType() {
		return issueType;
	}
}
