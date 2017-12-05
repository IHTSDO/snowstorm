package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ClassificationRepository extends ElasticsearchCrudRepository<Classification, String> {

}
