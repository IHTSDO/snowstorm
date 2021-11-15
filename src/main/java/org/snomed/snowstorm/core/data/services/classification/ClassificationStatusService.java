package org.snomed.snowstorm.core.data.services.classification;

import org.snomed.snowstorm.core.data.services.classification.pojo.ClassificationStatusResponse;

public interface ClassificationStatusService {
	ClassificationStatusResponse getStatusChange(String classificationId);
}
