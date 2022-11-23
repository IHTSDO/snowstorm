package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.hl7.fhir.r4.model.OperationOutcome;

public class SnowstormFHIRServerResponseException extends BaseServerResponseException {
	public SnowstormFHIRServerResponseException(int theStatusCode, String theMessage, OperationOutcome theBaseOperationOutcome) {
		super(theStatusCode, theMessage, theBaseOperationOutcome);
	}

	public SnowstormFHIRServerResponseException(int theStatusCode, String theMessage, OperationOutcome theBaseOperationOutcome, Throwable e) {
		super(theStatusCode, theMessage, e, theBaseOperationOutcome);
	}

	@Override
	public OperationOutcome getOperationOutcome() {
		return (OperationOutcome) super.getOperationOutcome();
	}

	public OperationOutcome.IssueType getIssueCode() {
		return getOperationOutcome().getIssueFirstRep().getCode();
	}
}
