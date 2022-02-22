package org.snomed.snowstorm.fhir.repositories;

import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface FHIRCodeSystemRepository extends ElasticsearchRepository<FHIRCodeSystemVersion, String> {

	FHIRCodeSystemVersion findFirstByUrlOrderByVersionDesc(String systemUrl);

}
