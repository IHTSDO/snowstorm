package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FHIRCodeSystemRepository extends ElasticsearchRepository<FHIRCodeSystemVersion, String> {

	FHIRCodeSystemVersion findFirstByUrlOrderByVersionDesc(String systemUrl);

	FHIRCodeSystemVersion findByUrlAndVersion(String systemUrl, String version);
}
