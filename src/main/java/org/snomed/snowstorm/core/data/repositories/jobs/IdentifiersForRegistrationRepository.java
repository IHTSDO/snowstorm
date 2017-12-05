package org.snomed.snowstorm.core.data.repositories.jobs;

import org.snomed.snowstorm.core.data.domain.jobs.IdentifiersForRegistration;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface IdentifiersForRegistrationRepository extends ElasticsearchCrudRepository<IdentifiersForRegistration, String> {

}
