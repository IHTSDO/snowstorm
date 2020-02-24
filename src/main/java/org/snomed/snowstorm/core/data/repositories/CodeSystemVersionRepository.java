package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface CodeSystemVersionRepository extends ElasticsearchCrudRepository<CodeSystemVersion, String> {

	Page<CodeSystemVersion> findByShortNameOrderByEffectiveDate(String shortName, Pageable pageRequest);

	Page<CodeSystemVersion> findByShortNameOrderByEffectiveDateDesc(String shortName, Pageable pageRequest);

	CodeSystemVersion findOneByShortNameAndEffectiveDate(String shortName, Integer effectiveDate);
}
