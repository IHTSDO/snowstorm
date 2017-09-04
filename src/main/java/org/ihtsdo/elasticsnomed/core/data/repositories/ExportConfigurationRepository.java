package org.ihtsdo.elasticsnomed.core.data.repositories;

import org.ihtsdo.elasticsnomed.core.data.domain.jobs.ExportConfiguration;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ExportConfigurationRepository extends ElasticsearchCrudRepository<ExportConfiguration, String> {

}
