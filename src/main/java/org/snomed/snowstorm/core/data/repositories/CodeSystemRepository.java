package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

import java.util.List;

public interface CodeSystemRepository extends ElasticsearchCrudRepository<CodeSystem, String> {

	CodeSystem findOneByBranchPath(String branchPath);

}
