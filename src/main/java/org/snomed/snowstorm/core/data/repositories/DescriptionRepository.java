package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.Description;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface DescriptionRepository extends ElasticsearchCrudRepository<Description, String> {

}
