package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ClassificationRepository extends ElasticsearchRepository<Classification, String> {

}
