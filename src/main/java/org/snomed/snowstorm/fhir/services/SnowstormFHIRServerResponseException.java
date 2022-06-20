package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;

public class SnowstormFHIRServerResponseException extends BaseServerResponseException {
	public SnowstormFHIRServerResponseException(int theStatusCode, String theMessage, IBaseOperationOutcome theBaseOperationOutcome) {
		super(theStatusCode, theMessage, theBaseOperationOutcome);
	}

	public SnowstormFHIRServerResponseException(int theStatusCode, String theMessage, IBaseOperationOutcome theBaseOperationOutcome, Throwable e) {
		super(theStatusCode, theMessage, e, theBaseOperationOutcome);
	}
}
