package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.Relationship;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface RelationshipRepository extends ElasticsearchRepository<Relationship, String> {

}
