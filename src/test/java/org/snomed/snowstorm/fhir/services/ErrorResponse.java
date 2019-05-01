package org.snomed.snowstorm.fhir.services;

public class ErrorResponse {

	private Integer timestamp;
	private Integer status;
	private String error;
	private String message;
	private String path;

	public Integer getTimestamp() {
	return timestamp;
	}

	public void setTimestamp(Integer timestamp) {
	this.timestamp = timestamp;
	}

	public Integer getStatus() {
	return status;
	}

	public void setStatus(Integer status) {
	this.status = status;
	}

	public String getError() {
	return error;
	}

	public void setError(String error) {
	this.error = error;
	}

	public String getMessage() {
	return message;
	}

	public void setMessage(String message) {
	this.message = message;
	}

	public String getPath() {
	return path;
	}

	public void setPath(String path) {
	this.path = path;
	}

}
