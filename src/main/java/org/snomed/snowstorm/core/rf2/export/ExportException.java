package org.snomed.snowstorm.core.rf2.export;

public class ExportException extends RuntimeException {
	public ExportException(String message) {
		super(message);
	}

	public ExportException(String message, Throwable cause) {
		super(message, cause);
	}
}
