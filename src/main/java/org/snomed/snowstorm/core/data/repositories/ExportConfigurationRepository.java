package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Date;
import java.util.List;

public interface ExportConfigurationRepository extends ElasticsearchRepository<ExportConfiguration, String> {
	List<ExportConfiguration> findByStartDateBefore(Date cutoff);
}
