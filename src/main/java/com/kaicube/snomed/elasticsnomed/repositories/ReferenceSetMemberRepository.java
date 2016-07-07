package com.kaicube.snomed.elasticsnomed.repositories;

import com.kaicube.snomed.elasticsnomed.domain.ReferenceSetMember;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ReferenceSetMemberRepository extends ElasticsearchCrudRepository<ReferenceSetMember, String> {

}
