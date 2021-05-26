package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Date;

public interface ClassificationRepository extends ElasticsearchRepository<Classification, String> {

	Classification findOneByPathAndStatusAndSaveDate(String path, ClassificationStatus status, Long saveDate);
}
