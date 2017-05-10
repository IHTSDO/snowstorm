package org.ihtsdo.elasticsnomed.repositories;

import org.ihtsdo.elasticsnomed.domain.QueryConcept;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface QueryIndexConceptRepository extends ElasticsearchCrudRepository<QueryConcept, String> {

}
