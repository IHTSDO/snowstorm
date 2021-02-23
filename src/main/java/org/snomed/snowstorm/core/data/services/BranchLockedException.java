package org.snomed.snowstorm.core.data.services;

public class BranchLockedException extends RuntimeException {
	public BranchLockedException(String message) {
		super(message);
	}

	public BranchLockedException(String message, Throwable cause) {
		super(message, cause);
	}
}
