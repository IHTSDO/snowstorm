package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.ReferenceSetMember;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ReferenceSetMemberRepository extends ElasticsearchCrudRepository<ReferenceSetMember, String> {

}
