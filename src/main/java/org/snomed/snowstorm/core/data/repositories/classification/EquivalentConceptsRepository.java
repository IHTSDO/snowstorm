package org.snomed.snowstorm.core.data.repositories.classification;

import org.snomed.snowstorm.core.data.domain.classification.EquivalentConcepts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface EquivalentConceptsRepository extends ElasticsearchRepository<EquivalentConcepts, String> {

	Page<EquivalentConcepts> findByClassificationId(String classificationId, Pageable pageRequest);

}
