package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.ValueSetWrapper;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface FHIRValuesetRepository extends ElasticsearchCrudRepository<ValueSetWrapper, String> {

}
