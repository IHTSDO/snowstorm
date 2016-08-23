package com.kaicube.elasticversioncontrol.repositories;

import com.kaicube.elasticversioncontrol.domain.Branch;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface BranchRepository extends ElasticsearchCrudRepository<Branch, String> {

}
