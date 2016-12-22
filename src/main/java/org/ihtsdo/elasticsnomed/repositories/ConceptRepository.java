package org.ihtsdo.elasticsnomed.repositories;

import org.ihtsdo.elasticsnomed.domain.Concept;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ConceptRepository extends ElasticsearchCrudRepository<Concept, String> {

}
