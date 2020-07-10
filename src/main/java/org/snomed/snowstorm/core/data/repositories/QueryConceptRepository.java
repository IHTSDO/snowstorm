package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface QueryConceptRepository extends ElasticsearchRepository<QueryConcept, String> {

}
