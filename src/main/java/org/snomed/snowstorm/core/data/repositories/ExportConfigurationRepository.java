package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.data.domain.jobs.ExportStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface ExportConfigurationRepository extends ElasticsearchRepository<ExportConfiguration, String> {
	List<ExportConfiguration> findByStartDateBetweenAndStatusIn(Date sixDaysAgo, Date yesterday, Collection<ExportStatus> statuses, Pageable pageable);
}
