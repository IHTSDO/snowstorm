package org.snomed.snowstorm;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.services.traceability.TraceabilityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

@PropertySource(value = "classpath:application.properties", encoding = "UTF-8")
@PropertySource(value = "classpath:application-test.properties", encoding = "UTF-8")
@TestConfiguration
@SpringBootApplication(
		exclude = {
				ElasticsearchRestClientAutoConfiguration.class,
				ElasticsearchDataAutoConfiguration.class,
				DataSourceAutoConfiguration.class})
public class TestConfig extends Config {

	private static final String ELASTIC_SEARCH_SERVER_VERSION = "8.11.1";

	// If running unit tests regularly using an already running local Elasticsearch container can speed up testing
	// To use this option create a test.properties file in the root of the project containing the value test.elasticsearch.local=true
	static final boolean useLocalElasticsearch = "true".equalsIgnoreCase(loadTestProperties().getProperty("test.elasticsearch.local"));

	private static Properties loadTestProperties() {
		Properties properties = new Properties();
		File file = new File("test.properties");
		if (file.isFile()) {
			try {
				properties.load(new FileReader(file));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return properties;
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(TestConfig.class);

	@Container
	private static final ElasticsearchContainer elasticsearchContainer;
	static
	{
		if (useLocalElasticsearch) {
			elasticsearchContainer = null;
		} else {
			if (!DockerClientFactory.instance().isDockerAvailable()) {
				LOGGER.error("No docker client available to run integration tests.");
				LOGGER.info("Integration tests use the TestContainers framework.(https://www.testcontainers.org)");
				LOGGER.info("TestContainers framework requires docker to be installed.(https://www.testcontainers.org/supported_docker_environment)");
				LOGGER.info("You can download docker(2.3.0.4) via (https://docs.docker.com/get-docker)");
				System.exit(-1);
			}
			elasticsearchContainer = new SnowstormElasticsearchContainer();
			elasticsearchContainer.start();
		}
	}

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@PostConstruct
	public void init() {
		traceabilityLogService.setEnabled(false);
		initialiseIndices(false);
	}

	public static class SnowstormElasticsearchContainer extends ElasticsearchContainer {
		public SnowstormElasticsearchContainer() {
			super("docker.elastic.co/elasticsearch/elasticsearch:" + ELASTIC_SEARCH_SERVER_VERSION);
			// these are mapped ports used by the test container the actual ports used might be different
			this.addFixedExposedPort(9235, 9235);
			this.addFixedExposedPort(9330, 9330);
			addEnv("xpack.security.enabled", "false");
			this.addEnv("cluster.name", "integration-test-cluster");
		}
	}

	static ElasticsearchContainer getElasticsearchContainerInstance() {
		return elasticsearchContainer;
	}

	@Override
	public @NotNull ClientConfiguration clientConfiguration() {
		if (!useLocalElasticsearch) {
            assert elasticsearchContainer != null;
			LOGGER.info("Test container Elasticsearch host {} ", elasticsearchContainer.getHttpHostAddress());
            return ClientConfiguration.builder()
					.connectedTo(elasticsearchContainer.getHttpHostAddress()).build();
		}
		return ClientConfiguration.builder()
				.connectedTo("localhost:9200").build();
	}
}
