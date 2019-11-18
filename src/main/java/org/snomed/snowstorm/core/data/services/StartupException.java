package org.snomed.snowstorm.core.data.services;

public class StartupException extends ServiceException {
	public StartupException(String message) {
		super(message);
	}

	public StartupException(String message, Throwable cause) {
		super(message, cause);
	}
}
