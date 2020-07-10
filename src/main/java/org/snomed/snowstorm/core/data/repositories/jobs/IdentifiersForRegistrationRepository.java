package org.snomed.snowstorm.core.data.repositories.jobs;

import org.snomed.snowstorm.core.data.domain.jobs.IdentifiersForRegistration;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface IdentifiersForRegistrationRepository extends ElasticsearchRepository<IdentifiersForRegistration, String> {

}
