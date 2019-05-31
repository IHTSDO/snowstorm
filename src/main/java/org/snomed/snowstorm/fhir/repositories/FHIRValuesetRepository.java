package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.FHIRValueSet;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface FHIRValuesetRepository extends ElasticsearchCrudRepository<FHIRValueSet, String> {

}
