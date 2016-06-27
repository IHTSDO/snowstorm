package com.kaicube.snomed.elasticsnomed.repositories;

import com.kaicube.snomed.elasticsnomed.domain.Relationship;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface RelationshipRepository extends ElasticsearchCrudRepository<Relationship, String> {

}
