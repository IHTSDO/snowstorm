package org.ihtsdo.elasticsnomed.core.data.services.classification.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.ihtsdo.elasticsnomed.core.data.domain.classification.Classification;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassificationStatusResponse {

	private Classification.Status status;
	private String errorMessage;
	private String developerMessage;

	public Classification.Status getStatus() {
		return status;
	}

	public void setStatus(Classification.Status status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getDeveloperMessage() {
		return developerMessage;
	}

	public void setDeveloperMessage(String developerMessage) {
		this.developerMessage = developerMessage;
	}
}
