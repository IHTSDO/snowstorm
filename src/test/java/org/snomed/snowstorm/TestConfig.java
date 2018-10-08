package org.snomed.snowstorm;

import org.snomed.snowstorm.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.rest.ElasticsearchRestClient;
import pl.allegro.tech.embeddedelasticsearch.EmbeddedElastic;
import pl.allegro.tech.embeddedelasticsearch.PopularProperties;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@PropertySource("application.properties")
@PropertySource("application-test.properties")
@TestConfiguration
public class TestConfig extends Config {

	private static final String ELASTIC_SEARCH_VERSION = "6.4.2";
	public static final List<String> DEFAULT_LANGUAGE_CODES = Collections.singletonList("en");

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Bean
	public ElasticsearchRestClient elasticsearchClient() {
		// Create and start a clean standalone Elasticsearch test instance
		String clusterName = "integration-test-cluster";
		int port = 9931;
		try {
			EmbeddedElastic.builder()
					.withElasticVersion(ELASTIC_SEARCH_VERSION)
					.withStartTimeout(30, TimeUnit.SECONDS)
					.withSetting(PopularProperties.CLUSTER_NAME, clusterName)
					.withSetting(PopularProperties.HTTP_PORT, port)
					.build()
					.start()
					.deleteIndices();
		} catch (InterruptedException | IOException e) {
			throw new RuntimeException("Failed to start standalone Elasticsearch instance.", e);
		}

		// Create client to to standalone instance
		return new ElasticsearchRestClient(new HashMap<>(), "http://localhost:" + port);
	}

}
