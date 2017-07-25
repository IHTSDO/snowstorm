package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.ReferenceSetType;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ReferenceSetTypeRepository extends ElasticsearchCrudRepository<ReferenceSetType, String> {

}
