package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

import java.util.List;

public interface CodeSystemVersionRepository extends ElasticsearchCrudRepository<CodeSystemVersion, String> {

	List<CodeSystemVersion> findByShortNameOrderByEffectiveDate(String shortName);
	
	List<CodeSystemVersion> findByShortNameOrderByEffectiveDateDesc(String shortName);

	//CodeSystemVersion findTopByShortNameOrderByEffectiveDateDesc(String shortName);

	CodeSystemVersion findOneByShortNameAndEffectiveDate(String shortName, Integer effectiveDate);
}
