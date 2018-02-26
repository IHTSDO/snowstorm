package org.snomed.snowstorm;

import io.kaicode.elasticvc.domain.Branch;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import pl.allegro.tech.embeddedelasticsearch.EmbeddedElastic;
import pl.allegro.tech.embeddedelasticsearch.PopularProperties;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@PropertySource("application-test.properties")
@TestConfiguration
public class TestConfig extends Config {

	private static final String ELASTIC_SEARCH_VERSION = "5.5.0";

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@Bean
	public Client elasticsearchClient() throws IOException {
		// Create and start a clean standalone Elasticsearch test instance
		String clusterName = "integration-test-cluster";
		int port = 9931;
		try {
			EmbeddedElastic.builder()
					.withElasticVersion(ELASTIC_SEARCH_VERSION)
					.withSetting(PopularProperties.CLUSTER_NAME, clusterName)
					.withSetting(PopularProperties.TRANSPORT_TCP_PORT, port)
					.build()
					.start()
					.deleteIndices();
		} catch (InterruptedException e) {
			throw new IOException("Failed to start standalone Elasticsearch instance.", e);
		}

		// Create client to to standalone instance
		Settings settings = Settings.builder().put("cluster.name", clusterName).build();
		return new PreBuiltTransportClient(settings)
				.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), port));
	}

}
