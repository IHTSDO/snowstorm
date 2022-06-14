package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Collection;
import java.util.List;

public interface FHIRConceptRepository extends ElasticsearchRepository<FHIRConcept, String> {

	FHIRConcept findFirstByCodeSystemVersionAndCode(String codeSystemVersion, String code);

	Page<FHIRConcept> findByCodeSystemVersionAndCodeIn(String codeSystemVersion, Collection<String> code, Pageable pageable);

	Page<FHIRConcept> findByCodeSystemVersion(String codeSystemVersionIdAndVersion, Pageable page);

	void deleteByCodeSystemVersionAndCodeIn(String codeSystemVersionIdAndVersion, List<String> code);
}
