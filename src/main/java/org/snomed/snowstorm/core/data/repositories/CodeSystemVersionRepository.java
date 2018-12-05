package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

import java.util.List;

public interface CodeSystemVersionRepository extends ElasticsearchCrudRepository<CodeSystemVersion, String> {

	List<CodeSystemVersion> findByShortNameOrderByEffectiveDate(String shortName);

	CodeSystemVersion findOneByShortNameOrderByEffectiveDateDesc(String shortName);

	CodeSystemVersion findOneByShortNameAndEffectiveDate(String shortName, Integer effectiveDate);
}
