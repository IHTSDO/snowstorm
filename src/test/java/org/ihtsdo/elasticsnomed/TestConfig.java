package org.ihtsdo.elasticsnomed;

import io.kaicode.elasticvc.domain.Branch;

import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.DummyIdentifierStorage;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.IdentifierCacheManager;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.IdentifierStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import javax.annotation.PostConstruct;

import java.net.UnknownHostException;

@PropertySource("application-test.properties")
public class TestConfig extends Config {

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	@Override
	@Bean 
	public IdentifierStorage getIdentifierStorage() {
		//Uncomment this line to run integration test with real cis supplied sctids
		//return new CISClient();
		return new DummyIdentifierStorage();
	}
	
	@Bean 
	public IdentifierCacheManager getIdentifierCacheManager() {
		return new IdentifierCacheManager();
	}

	@PostConstruct
	public void cleanUp() throws UnknownHostException {
		logger.info("Deleting all existing entities before tests start");
		elasticsearchTemplate.deleteIndex(Concept.class);
		elasticsearchTemplate.deleteIndex(Description.class);
		elasticsearchTemplate.deleteIndex(Relationship.class);
		elasticsearchTemplate.deleteIndex(ReferenceSetMember.class);
		elasticsearchTemplate.deleteIndex(QueryConcept.class);
		elasticsearchTemplate.deleteIndex(Branch.class);
	}

}
