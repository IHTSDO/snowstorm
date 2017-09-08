package org.ihtsdo.elasticsnomed.core.data.services.cis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CISBulkRequestResponse {

	private String id;
	private int statusCode;
	private String message;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
