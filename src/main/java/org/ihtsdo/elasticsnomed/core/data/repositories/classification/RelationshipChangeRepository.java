package org.ihtsdo.elasticsnomed.core.data.repositories.classification;

import org.ihtsdo.elasticsnomed.core.data.domain.classification.Classification;
import org.ihtsdo.elasticsnomed.core.data.domain.classification.RelationshipChange;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface RelationshipChangeRepository extends ElasticsearchCrudRepository<RelationshipChange, String> {

}
