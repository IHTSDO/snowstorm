package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.RefsetConceptsLookup;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface RefsetConceptsLookupRepository extends ElasticsearchRepository<RefsetConceptsLookup, String> {
}
