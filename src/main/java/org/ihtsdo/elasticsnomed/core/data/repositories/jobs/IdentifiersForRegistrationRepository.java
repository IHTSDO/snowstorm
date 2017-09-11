package org.ihtsdo.elasticsnomed.core.data.repositories.jobs;

import org.ihtsdo.elasticsnomed.core.data.domain.jobs.IdentifiersForRegistration;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface IdentifiersForRegistrationRepository extends ElasticsearchCrudRepository<IdentifiersForRegistration, String> {

}
