package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CodeSystemRepository extends ElasticsearchRepository<CodeSystem, String> {

}
