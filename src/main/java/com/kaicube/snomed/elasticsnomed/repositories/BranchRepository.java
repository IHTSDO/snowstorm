package com.kaicube.snomed.elasticsnomed.repositories;

import com.kaicube.snomed.elasticsnomed.domain.Branch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

import java.util.Set;

public interface BranchRepository extends ElasticsearchCrudRepository<Branch, String> {

	Page<Branch> findAll(Pageable pageable);

	Iterable<Branch> findByPathIn(Set<String> paths);

	Branch findByPath(String path);
}
