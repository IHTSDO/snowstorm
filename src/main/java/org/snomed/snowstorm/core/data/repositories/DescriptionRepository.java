package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.Description;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DescriptionRepository extends ElasticsearchRepository<Description, String> {

}
