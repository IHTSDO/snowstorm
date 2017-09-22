package org.ihtsdo.elasticsnomed.core.data.repositories.classification;

import org.ihtsdo.elasticsnomed.core.data.domain.classification.RelationshipChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface RelationshipChangeRepository extends ElasticsearchCrudRepository<RelationshipChange, String> {

	Page<RelationshipChange> findByClassificationId(String classificationId, Pageable pageRequest);
}
