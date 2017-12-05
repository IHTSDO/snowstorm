package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface QueryConceptRepository extends ElasticsearchCrudRepository<QueryConcept, String> {

}
