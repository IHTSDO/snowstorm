package org.ihtsdo.elasticsnomed.repositories;

import org.ihtsdo.elasticsnomed.domain.ReferenceSetMember;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ReferenceSetMemberRepository extends ElasticsearchCrudRepository<ReferenceSetMember, String> {

}
