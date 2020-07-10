package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.BranchMergeJob;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface BranchMergeJobRepository extends ElasticsearchRepository<BranchMergeJob, String> {
}
