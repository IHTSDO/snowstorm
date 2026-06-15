package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.FHIRConceptMap;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Collection;

public interface FHIRConceptMapRepository extends ElasticsearchRepository<FHIRConceptMap, String> {

	Collection<FHIRConceptMap> findAllByUrl(String url);

}
