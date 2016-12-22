package org.ihtsdo.elasticsnomed.services;

public class ServiceException extends Exception {
	public ServiceException(String message) {
		super(message);
	}

	public ServiceException(String message, Throwable cause) {
		super(message, cause);
	}
}
