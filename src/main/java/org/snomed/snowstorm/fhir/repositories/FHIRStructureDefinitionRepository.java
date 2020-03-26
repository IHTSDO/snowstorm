package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.StructureDefinitionWrapper;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface FHIRStructureDefinitionRepository extends ElasticsearchCrudRepository<StructureDefinitionWrapper, String> {

}
