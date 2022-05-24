package org.snomed.snowstorm.fhir.repositories;

import org.snomed.snowstorm.fhir.domain.FHIRMapElement;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface FHIRMapElementRepository extends ElasticsearchRepository<FHIRMapElement, String> {

	List<FHIRMapElement> findAllByGroupId(String groupId);

}
