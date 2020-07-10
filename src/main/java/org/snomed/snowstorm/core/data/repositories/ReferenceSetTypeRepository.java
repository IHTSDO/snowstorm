package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.ReferenceSetType;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ReferenceSetTypeRepository extends ElasticsearchRepository<ReferenceSetType, String> {

}
