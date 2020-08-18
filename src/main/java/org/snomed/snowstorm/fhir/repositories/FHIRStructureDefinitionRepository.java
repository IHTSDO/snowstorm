package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.StructureDefinitionWrapper;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FHIRStructureDefinitionRepository extends ElasticsearchRepository<StructureDefinitionWrapper, String> {

}
