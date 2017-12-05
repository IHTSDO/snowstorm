package org.snomed.snowstorm.rest.pojo;

import org.snomed.snowstorm.core.data.domain.classification.Classification;

public class ClassificationUpdateRequest {

	private Classification.Status status;

	public Classification.Status getStatus() {
		return status;
	}

	public void setStatus(Classification.Status status) {
		this.status = status;
	}
}
