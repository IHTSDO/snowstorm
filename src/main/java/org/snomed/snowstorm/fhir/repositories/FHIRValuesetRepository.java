package org.snomed.snowstorm.fhir.repositories;

import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface FHIRValuesetRepository extends ElasticsearchCrudRepository<ValueSet, String> {

}
