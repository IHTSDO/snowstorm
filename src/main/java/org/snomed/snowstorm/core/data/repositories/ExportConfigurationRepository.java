package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ExportConfigurationRepository extends ElasticsearchRepository<ExportConfiguration, String> {

}
