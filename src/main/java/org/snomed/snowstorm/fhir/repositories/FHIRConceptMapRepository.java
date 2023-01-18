package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.FHIRConceptMap;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface FHIRConceptMapRepository extends ElasticsearchRepository<FHIRConceptMap, String> {

	List<FHIRConceptMap> findAllByUrl(String url);
}
