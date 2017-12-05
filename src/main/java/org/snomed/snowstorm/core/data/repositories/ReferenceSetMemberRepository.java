package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ReferenceSetMemberRepository extends ElasticsearchCrudRepository<ReferenceSetMember, String> {

}
