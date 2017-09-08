package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.QueryConcept;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface QueryConceptRepository extends ElasticsearchCrudRepository<QueryConcept, String> {

}
