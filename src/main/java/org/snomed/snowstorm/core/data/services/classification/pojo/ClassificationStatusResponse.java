package org.snomed.snowstorm.core.data.services.classification.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassificationStatusResponse {
	private String id;
	private ClassificationStatus status;
	private String errorMessage;
	private String developerMessage;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ClassificationStatus getStatus() {
		return status;
	}

	public void setStatus(ClassificationStatus status) {
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
