package org.snomed.snowstorm.core.data.services;

public class TooCostlyException extends RuntimeServiceException {
	public TooCostlyException(String message) {
		super(message);
	}
}
