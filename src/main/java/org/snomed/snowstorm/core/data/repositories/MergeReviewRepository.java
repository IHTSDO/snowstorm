package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface MergeReviewRepository extends ElasticsearchRepository<MergeReview, String> {

}
