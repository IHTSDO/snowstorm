package org.snomed.snowstorm.fhir.exceptions;

public class MissingParameterException extends RuntimeException {

	public MissingParameterException(final String message) {
		super(message);
	}
}
