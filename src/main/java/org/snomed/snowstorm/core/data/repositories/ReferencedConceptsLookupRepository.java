package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ReferencedConceptsLookupRepository extends ElasticsearchRepository<ReferencedConceptsLookup, String> {
}
