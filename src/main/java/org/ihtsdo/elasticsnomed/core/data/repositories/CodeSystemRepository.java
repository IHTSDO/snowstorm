package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.CodeSystem;
import org.ihtsdo.elasticsnomed.core.data.domain.CodeSystemVersion;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

import java.util.List;

public interface CodeSystemRepository extends ElasticsearchCrudRepository<CodeSystem, String> {

}
