package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

import java.util.Optional;

public interface CodeSystemRepository extends ElasticsearchCrudRepository<CodeSystem, String> {

	Optional<CodeSystem> findByBranchPath(String branchPath);

}
