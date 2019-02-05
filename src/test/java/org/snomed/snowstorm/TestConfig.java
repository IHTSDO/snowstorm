package org.snomed.snowstorm;

import org.snomed.snowstorm.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.aws.autoconfigure.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.rest.ElasticsearchRestClient;
import pl.allegro.tech.embeddedelasticsearch.EmbeddedElastic;
import pl.allegro.tech.embeddedelasticsearch.PopularProperties;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@PropertySource("application.properties")
@PropertySource("application-test.properties")
@TestConfiguration
@SpringBootApplication(
		exclude = {ContextCredentialsAutoConfiguration.class,
				ContextInstanceDataAutoConfiguration.class,
				ContextRegionProviderAutoConfiguration.class,
				ContextResourceLoaderAutoConfiguration.class,
				ContextStackAutoConfiguration.class,
				ElasticsearchAutoConfiguration.class,
				ElasticsearchDataAutoConfiguration.class})
public class TestConfig extends Config {

	private static final String ELASTIC_SEARCH_VERSION = "6.4.2";
	public static final List<String> DEFAULT_LANGUAGE_CODES = Collections.singletonList("en");

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	private EmbeddedElastic embeddedElastic;
	private boolean fullyStarted;

	@Bean
	public ElasticsearchRestClient elasticsearchClient() {
		// Create and start a clean standalone Elasticsearch test instance
		String clusterName = "integration-test-cluster";
		int port = 9931;
		try {
			embeddedElastic = EmbeddedElastic.builder()
					.withElasticVersion(ELASTIC_SEARCH_VERSION)
					.withStartTimeout(30, TimeUnit.SECONDS)
					.withSetting(PopularProperties.CLUSTER_NAME, clusterName)
					.withSetting(PopularProperties.HTTP_PORT, port)
					.build();

			embeddedElastic
					.start()
					.deleteIndices();
			fullyStarted = true;
		} catch (InterruptedException | IOException e) {
			throw new RuntimeException("Failed to start standalone Elasticsearch instance.", e);
		}

		// Create client to to standalone instance
		return new ElasticsearchRestClient(new HashMap<>(), "http://localhost:" + port);
	}

	@PreDestroy
	public void shutdown() {
		if (!fullyStarted) {
			embeddedElastic.stop();
		}
	}

}
