package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.Relationship;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface RelationshipRepository extends ElasticsearchCrudRepository<Relationship, String> {

}
