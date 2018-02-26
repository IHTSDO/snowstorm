package org.snomed.snowstorm.rest.pojo;

import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;

public class ClassificationUpdateRequest {

	private ClassificationStatus status;

	public ClassificationStatus getStatus() {
		return status;
	}

	public void setStatus(ClassificationStatus status) {
		this.status = status;
	}
}
