package org.snomed.snowstorm.core.data.services.postcoordination;

import org.snomed.snowstorm.core.data.services.ServiceException;

public class TransformationException extends ServiceException {
	public TransformationException(String message) {
		super(message);
	}

	public TransformationException(String message, Throwable cause) {
		super(message, cause);
	}
}
