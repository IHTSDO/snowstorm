package org.snomed.snowstorm.core.data.services;

public class RuntimeServiceException extends RuntimeException {
	public RuntimeServiceException(String message) {
		super(message);
	}

	public RuntimeServiceException(String message, Throwable cause) {
		super(message, cause);
	}
}
