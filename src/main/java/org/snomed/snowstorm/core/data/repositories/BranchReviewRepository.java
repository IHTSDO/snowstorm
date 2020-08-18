package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.review.BranchReview;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Date;

public interface BranchReviewRepository extends ElasticsearchRepository<BranchReview, String> {

	@Query("{\"bool\":{\"must\":[ {\"term\":{\"source.path\":\"?0\"}}, {\"term\":{\"source.baseTimestamp\":\"?1\"}}, {\"term\":{\"source.headTimestamp\":\"?2\"}}, " +
			"{\"term\":{\"target.path\":\"?3\"}}, {\"term\":{\"target.baseTimestamp\":\"?4\"}}, {\"term\":{\"target.headTimestamp\":\"?5\"}} ]}}")
	BranchReview findBySourceAndTargetPathsAndStates(String sourcePath, long sourceBase, long sourceHead, String targetPath, long targetBase, long targetHead);

}
