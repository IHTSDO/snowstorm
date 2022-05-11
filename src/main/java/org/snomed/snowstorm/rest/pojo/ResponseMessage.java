package org.snomed.snowstorm.rest.pojo;

public class ResponseMessage {

	private final String message;

	public ResponseMessage(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
