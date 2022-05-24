package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.FHIRConceptMap;
import org.snomed.snowstorm.fhir.domain.ValueSetWrapper;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FHIRConceptMapRepository extends ElasticsearchRepository<FHIRConceptMap, String> {

}
