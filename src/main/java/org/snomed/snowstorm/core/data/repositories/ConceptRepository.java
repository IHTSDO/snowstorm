package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ConceptRepository extends ElasticsearchCrudRepository<Concept, String> {

}
