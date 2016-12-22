package org.ihtsdo.elasticsnomed.repositories;

import org.ihtsdo.elasticsnomed.domain.Relationship;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface RelationshipRepository extends ElasticsearchCrudRepository<Relationship, String> {

}
