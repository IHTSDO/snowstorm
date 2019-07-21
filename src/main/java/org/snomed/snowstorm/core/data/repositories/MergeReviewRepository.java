package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface MergeReviewRepository extends ElasticsearchCrudRepository<MergeReview, String> {

}
