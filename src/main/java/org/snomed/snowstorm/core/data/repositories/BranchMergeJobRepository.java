package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.BranchMergeJob;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface BranchMergeJobRepository extends ElasticsearchCrudRepository<BranchMergeJob, String> {
}
