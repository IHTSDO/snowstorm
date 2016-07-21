package com.kaicube.snomed.elasticsnomed.repositories;

import com.kaicube.snomed.elasticsnomed.domain.QueryIndexConcept;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface QueryIndexConceptRepository extends ElasticsearchCrudRepository<QueryIndexConcept, String> {

}
