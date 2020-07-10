package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ReferenceSetMemberRepository extends ElasticsearchRepository<ReferenceSetMember, String> {

}
