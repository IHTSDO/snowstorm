package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface DescriptionRepository extends ElasticsearchCrudRepository<Description, String> {

}
