package org.snomed.snowstorm;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.services.TraceabilityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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

	private static final String ELASTIC_SEARCH_SERVER_VERSION = "6.8.0";
	public static final String DEFAULT_LANGUAGE_CODE = "en";

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	private static EmbeddedElastic testElasticsearchSingleton;
	private static File installationDirectory;
	private static final int PORT = 9931;

	private final boolean testElasticsearchUseLocal = false;

	@PostConstruct
	public void init() {
		traceabilityLogService.setEnabled(false);
	}

	@Bean
	public ElasticsearchRestClient elasticsearchClient(@Value("${test.elasticsearch.start-timeout-mins:2}") int unitTestElasticsearchStartTimeoutMins) {

		if (testElasticsearchUseLocal) {
			return new ElasticsearchRestClient(new HashMap<>(), "http://localhost:9200");
		}

		// Share the Elasticsearch instance between test contexts
		if (testElasticsearchSingleton == null) {
			// Create and start a clean standalone Elasticsearch test instance
			String clusterName = "snowstorm-integration-test-cluster";

			try {
				installationDirectory = new File(System.getProperty("java.io.tmpdir"), "embedded-elasticsearch-temp-dir");
				File downloadDir = null;
				if (System.getProperty("user.home") != null) {
					downloadDir = new File(new File(System.getProperty("user.home"), "tmp"), "embedded-elasticsearch-download-cache");
					downloadDir.mkdirs();
				}
				LoggerFactory.getLogger(getClass()).info("Starting Elasticsearch node for unit tests. Timeout is {} minutes.", unitTestElasticsearchStartTimeoutMins);
				testElasticsearchSingleton = EmbeddedElastic.builder()
						.withElasticVersion(ELASTIC_SEARCH_SERVER_VERSION)
						.withStartTimeout(unitTestElasticsearchStartTimeoutMins, TimeUnit.MINUTES)
						.withSetting(PopularProperties.CLUSTER_NAME, clusterName)
						.withSetting(PopularProperties.HTTP_PORT, PORT)
						.withSetting("cluster.routing.allocation.disk.threshold_enabled", false)
						// Manually delete installation directory to prevent verbose error logging
						.withCleanInstallationDirectoryOnStop(false)
						.withDownloadDirectory(downloadDir)
						.withInstallationDirectory(installationDirectory)
						.build();
				testElasticsearchSingleton
						.start()
						.deleteIndices();
			} catch (InterruptedException | IOException e) {
				throw new RuntimeException("Failed to start standalone Elasticsearch instance.", e);
			}
		}

		// Create client to to standalone instance
		return new ElasticsearchRestClient(new HashMap<>(), "http://localhost:" + PORT);
	}

	@PreDestroy
	public void shutdown() {
		synchronized (TestConfig.class) {
			Logger logger = LoggerFactory.getLogger(getClass());
			if (testElasticsearchSingleton != null) {
				try {
					testElasticsearchSingleton.stop();
				} catch (Exception e) {
					logger.info("The test Elasticsearch instance threw an exception during shutdown, probably due to multiple test contexts. This can be ignored.");
					logger.debug("The test Elasticsearch instance threw an exception during shutdown.", e);
				}
				if (installationDirectory != null && installationDirectory.exists()) {
					try {
						FileUtils.forceDelete(installationDirectory);
					} catch (IOException e) {
						logger.info("Error deleting the test Elasticsearch installation directory from temp {}", installationDirectory.getAbsolutePath());
					}
				}
			}
			testElasticsearchSingleton = null;
		}
	}

}
