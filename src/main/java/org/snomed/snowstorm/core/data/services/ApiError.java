package org.snomed.snowstorm.core.data.services;

import java.util.Map;

public class ApiError {

	private String message;
	private String developerMessage;
	private Map<String, Object> additionalInfo;

	public ApiError() {
	}

	public ApiError(String message, String developerMessage) {
		this.message = message;
		this.developerMessage = developerMessage;
	}

	public ApiError(String message, String developerMessage, Map<String, Object> additionalInfo) {
		this.message = message;
		this.developerMessage = developerMessage;
		this.additionalInfo = additionalInfo;
	}

	public String getMessage() {
		return message;
	}

	public String getDeveloperMessage() {
		return developerMessage;
	}

	public Map<String, Object> getAdditionalInfo() {
		return additionalInfo;
	}

	@Override
	public String toString() {
		return "ApiError{" +
				"message='" + message + '\'' +
				", developerMessage='" + developerMessage + '\'' +
				", additionalInfo=" + additionalInfo +
				'}';
	}
}
