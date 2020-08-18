package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.ValueSetWrapper;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FHIRValuesetRepository extends ElasticsearchRepository<ValueSetWrapper, String> {

}
