package org.ihtsdo.elasticsnomed.services;

public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
