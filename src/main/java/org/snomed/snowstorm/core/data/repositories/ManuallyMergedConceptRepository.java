package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.review.ManuallyMergedConcept;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

import java.util.List;

public interface ManuallyMergedConceptRepository extends ElasticsearchCrudRepository<ManuallyMergedConcept, String> {

	ManuallyMergedConcept findOneByMergeReviewIdAndConceptId(String mergeReviewId, String conceptId);

	Page<ManuallyMergedConcept> findByMergeReviewId(String mergeReviewId, Pageable pageRequest);

}
