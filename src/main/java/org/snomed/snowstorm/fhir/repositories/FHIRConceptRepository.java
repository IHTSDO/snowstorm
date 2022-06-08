package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FHIRConceptRepository extends ElasticsearchRepository<FHIRConcept, String> {

	FHIRConcept findFirstByCodeSystemVersionAndCode(String codeSystemVersion, String code);

}
