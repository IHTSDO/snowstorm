package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ConceptRepository extends ElasticsearchCrudRepository<Concept, String> {

}
