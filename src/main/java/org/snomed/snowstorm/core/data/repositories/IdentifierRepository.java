package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.Identifier;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface IdentifierRepository extends ElasticsearchRepository<Identifier, String> {

}
