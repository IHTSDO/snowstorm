package org.snomed.snowstorm.core.data.repositories.classification;

import org.snomed.snowstorm.core.data.domain.classification.RelationshipChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface RelationshipChangeRepository extends ElasticsearchCrudRepository<RelationshipChange, String> {

	Page<RelationshipChange> findByClassificationId(String classificationId, Pageable pageRequest);

	Page<RelationshipChange> findByClassificationIdAndSourceId(String classificationId, String sourceId, Pageable pageRequest);
}
