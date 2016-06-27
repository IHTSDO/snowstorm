package com.kaicube.snomed.elasticsnomed.repositories;

import com.kaicube.snomed.elasticsnomed.domain.Description;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface DescriptionRepository extends ElasticsearchCrudRepository<Description, String> {

}
