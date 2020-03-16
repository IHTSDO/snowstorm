package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.OperationOutcome.IssueType;

public class FHIROperationException extends Exception {

	private static final long serialVersionUID = 1L;
	
	IssueType issueType;
	
	public FHIROperationException (IssueType issueType, String message) {
		super(message);
		this.issueType = issueType;
	}
	
	public FHIROperationException (IssueType issueType, String message, Exception e) {
		super(message, e);
		this.issueType = issueType;
	}
	
	public IssueType getIssueType() {
		return issueType;
	}
	
	public String getMessage() {
		String msg = super.getMessage();
		if (getCause() != null) {
			msg += " due to: " + getCause().getMessage();
		}
		return msg;
	}
}
