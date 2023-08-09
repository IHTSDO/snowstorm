package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.FHIRValueSet;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface FHIRValueSetRepository extends ElasticsearchRepository<FHIRValueSet, String> {

	List<FHIRValueSet> findAllByUrl(String url);

}
