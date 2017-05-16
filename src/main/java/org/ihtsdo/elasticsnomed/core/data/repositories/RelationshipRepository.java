package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface RelationshipRepository extends ElasticsearchCrudRepository<Relationship, String> {

}
