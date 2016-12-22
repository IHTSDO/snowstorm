package org.ihtsdo.elasticsnomed.repositories;

import org.ihtsdo.elasticsnomed.domain.QueryIndexConcept;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface QueryIndexConceptRepository extends ElasticsearchCrudRepository<QueryIndexConcept, String> {

}
