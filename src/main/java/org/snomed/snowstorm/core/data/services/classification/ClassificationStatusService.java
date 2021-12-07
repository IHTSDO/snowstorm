package org.snomed.snowstorm.core.data.services.classification;

import org.snomed.snowstorm.core.data.services.classification.pojo.ClassificationStatusResponse;

import java.util.Optional;

public interface ClassificationStatusService {
	Optional<ClassificationStatusResponse> getStatusChange(String classificationId);
}
