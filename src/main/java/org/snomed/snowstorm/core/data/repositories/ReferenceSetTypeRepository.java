package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.ReferenceSetType;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ReferenceSetTypeRepository extends ElasticsearchCrudRepository<ReferenceSetType, String> {

}
