package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ConceptRepository extends ElasticsearchRepository<Concept, String> {

}
