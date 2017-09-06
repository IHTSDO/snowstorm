package org.ihtsdo.elasticsnomed.core.data.repositories.classification;

import org.ihtsdo.elasticsnomed.core.data.domain.classification.EquivalentConcepts;
import org.ihtsdo.elasticsnomed.core.data.domain.classification.RelationshipChange;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface EquivalentConceptsRepository extends ElasticsearchCrudRepository<EquivalentConcepts, String> {

}
