package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.classification.Classification;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ClassificationRepository extends ElasticsearchCrudRepository<Classification, String> {

}
