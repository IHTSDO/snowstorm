package org.ihtsdo.elasticsnomed.repositories;

import org.ihtsdo.elasticsnomed.domain.Description;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface DescriptionRepository extends ElasticsearchCrudRepository<Description, String> {

}
