package org.snomed.snowstorm.core.data.services.postcoordination;

import org.snomed.snowstorm.core.data.services.ServiceException;

public class ExpressionValidationException extends ServiceException {
	public ExpressionValidationException(String message) {
		super(message);
	}

	public ExpressionValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}
