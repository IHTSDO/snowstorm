package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.Classification;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ClassificationRepository extends ElasticsearchCrudRepository<Classification, String> {

}
