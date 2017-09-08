package org.ihtsdo.elasticsnomed.rest.pojo;

import org.ihtsdo.elasticsnomed.core.data.domain.classification.Classification;

public class ClassificationUpdateRequest {

	private Classification.Status status;

	public Classification.Status getStatus() {
		return status;
	}

	public void setStatus(Classification.Status status) {
		this.status = status;
	}
}
