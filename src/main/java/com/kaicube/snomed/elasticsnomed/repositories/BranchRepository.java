package com.kaicube.snomed.elasticsnomed.repositories;

import com.kaicube.snomed.elasticsnomed.domain.Branch;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface BranchRepository extends ElasticsearchCrudRepository<Branch, String> {

}
