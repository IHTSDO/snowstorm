package org.snomed.snowstorm;

import io.kaicode.elasticvc.domain.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.core.data.domain.classification.EquivalentConcepts;
import org.snomed.snowstorm.core.data.domain.classification.RelationshipChange;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.data.domain.jobs.IdentifiersForRegistration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import javax.annotation.PostConstruct;
import java.net.UnknownHostException;

@PropertySource("application-test.properties")
@TestConfiguration
public class TestConfig extends Config {

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void cleanUp() throws UnknownHostException {
		logger.info("Deleting all existing entities before tests start");
		elasticsearchTemplate.deleteIndex(Branch.class);

		elasticsearchTemplate.deleteIndex(Concept.class);
		elasticsearchTemplate.deleteIndex(Description.class);
		elasticsearchTemplate.deleteIndex(Relationship.class);
		elasticsearchTemplate.deleteIndex(ReferenceSetType.class);
		elasticsearchTemplate.deleteIndex(ReferenceSetMember.class);
		elasticsearchTemplate.deleteIndex(QueryConcept.class);

		elasticsearchTemplate.deleteIndex(Classification.class);
		elasticsearchTemplate.deleteIndex(EquivalentConcepts.class);
		elasticsearchTemplate.deleteIndex(RelationshipChange.class);
		elasticsearchTemplate.deleteIndex(ExportConfiguration.class);
		elasticsearchTemplate.deleteIndex(IdentifiersForRegistration.class);

		elasticsearchTemplate.deleteIndex(CodeSystem.class);
		elasticsearchTemplate.deleteIndex(CodeSystemVersion.class);
	}

}
