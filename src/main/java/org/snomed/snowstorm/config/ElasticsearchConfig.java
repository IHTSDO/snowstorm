package org.snomed.snowstorm.config;

import com.google.common.base.Strings;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.repositories.config.IndexNameProvider;
import jakarta.annotation.PostConstruct;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.elasticsearch.DateToLongConverter;
import org.snomed.snowstorm.config.elasticsearch.LongToDateConverter;
import org.snomed.snowstorm.core.data.domain.Annotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.rest5_client.Rest5Clients;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.support.HttpHeaders;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;

public class ElasticsearchConfig extends ElasticsearchConfiguration {

	public static final String INDEX_MAX_TERMS_COUNT = "index.max_terms_count";

	private static final Duration CONNECTION_REQUEST_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(360);
	private static final int MAX_CONNECTIONS = 100;

	@Value("${elasticsearch.username}")
	private String elasticsearchUsername;

	@Value("${elasticsearch.password}")
	private String elasticsearchPassword;

	@Value("${elasticsearch.index.prefix}")
	private String indexNamePrefix;

	@Value("${elasticsearch.index.shards}")
	short indexShards;

	@Value("${elasticsearch.index.replicas}")
	short indexReplicas;

	@Value("${snowstorm.aws.request-signing.enabled}")
	private Boolean awsRequestSigning;

	@Value("${elasticsearch.api-key}")
	private String apiKey;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() throws IOException {
		initialiseIndices(false);
	}

	@Override
	public @NotNull ClientConfiguration clientConfiguration() {
		final String[] urls = elasticsearchProperties().getUrls();
		for (String url : urls) {
			logger.info("Elasticsearch host: {}", url);
		}
		boolean useApiKey = !Strings.isNullOrEmpty(apiKey);
		HttpHeaders apiKeyHeaders = new HttpHeaders();
		if (useApiKey) {
			logger.info("Using API key authentication.");
			apiKeyHeaders.add("Authorization", "ApiKey " + apiKey);
		}

		ClientConfiguration.MaybeSecureClientConfigurationBuilder hostBuilder = ClientConfiguration.builder().connectedTo(getHosts(urls));
		ClientConfiguration.TerminalClientConfigurationBuilder builder = useHttps(urls) ? hostBuilder.usingSsl() : hostBuilder;

		builder.withDefaultHeaders(apiKeyHeaders)
				.withConnectTimeout(CONNECT_TIMEOUT)
				.withSocketTimeout(SOCKET_TIMEOUT)
				.withClientConfigurer(connectionRequestTimeout())
				.withClientConfigurer(connectionPoolSize());
		logger.info("Configured Elasticsearch client timeouts: connectionRequestTimeout={}s, connectTimeout={}s, socketTimeout={}s",
				CONNECTION_REQUEST_TIMEOUT.toSeconds(), CONNECT_TIMEOUT.toSeconds(), SOCKET_TIMEOUT.toSeconds());

		if (!Strings.isNullOrEmpty(elasticsearchUsername) && !Strings.isNullOrEmpty(elasticsearchPassword)) {
			// withBasicAuth is applied as an Authorization header when the configuration is built, which would replace
			// the API key header set above, so only apply it when no API key is configured. The API key takes precedence.
			if (useApiKey) {
				logger.warn("Both elasticsearch.api-key and elasticsearch.username/password are set. " +
						"The API key takes precedence and basic authentication will not be used.");
			} else {
				logger.info("Using basic authentication.");
				builder.withBasicAuth(elasticsearchUsername, elasticsearchPassword);
			}
		}

		if (awsRequestSigning != null && awsRequestSigning) {
			logger.info("Signing Elasticsearch requests with AWS credentials.");
			builder.withClientConfigurer(awsRequestSigning());
		}

		return builder.build();
	}

	private boolean useHttps(String[] urls) {
		for (String url : urls) {
			if (url.startsWith("https://")) {
				return true;
			}
		}
		return false;
	}

	/*
	 * Rest5Clients derives the connection request timeout from the socket timeout. This callback runs after
	 * that default, restoring the 30 seconds configured here before spring-data-elasticsearch 6.
	 */
	private Rest5Clients.ElasticsearchRequestConfigCallback connectionRequestTimeout() {
		return Rest5Clients.ElasticsearchRequestConfigCallback.from(requestConfigBuilder ->
				requestConfigBuilder.setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECTION_REQUEST_TIMEOUT.toMillis())));
	}

	private Rest5Clients.ElasticsearchConnectionManagerCallback connectionPoolSize() {
		return Rest5Clients.ElasticsearchConnectionManagerCallback.from(connectionManagerBuilder ->
				connectionManagerBuilder
						.setMaxConnTotal(MAX_CONNECTIONS)
						.setMaxConnPerRoute(MAX_CONNECTIONS));
	}

	private Rest5Clients.ElasticsearchHttpClientConfigurationCallback awsRequestSigning() {
		return Rest5Clients.ElasticsearchHttpClientConfigurationCallback.from(httpClientBuilder ->
				httpClientBuilder.addRequestInterceptorFirst(awsInterceptor("es")));
	}

	private AwsRequestSigningApacheV5Interceptor awsInterceptor(String serviceName) {
		return new AwsRequestSigningApacheV5Interceptor(serviceName, Aws4Signer.create(), DefaultCredentialsProvider.create(), DefaultAwsRegionProviderChain.builder().build().getRegion());
	}

	private static String[] getHosts(String[] hosts) {
		List<HttpHost> httpHosts = new ArrayList<>();
		for (String host : hosts) {
			try {
				httpHosts.add(HttpHost.create(host));
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("Invalid Elasticsearch URL configured: " + host, e);
			}
		}
		return httpHosts.stream().map(HttpHost::toHostString).toList().toArray(new String[]{});
	}

	@Bean
	@Override
	public @NotNull ElasticsearchCustomConversions elasticsearchCustomConversions() {
		return new ElasticsearchCustomConversions(
				Arrays.asList(new DateToLongConverter(), new LongToDateConverter()));
	}

	@Bean
	public ElasticsearchProperties elasticsearchProperties() {
		return new ElasticsearchProperties();
	}

	@Bean
	public IndexNameProvider indexNameProvider() {
		return new IndexNameProvider(indexNamePrefix);
	}

	protected void initialiseIndices(boolean deleteExisting) {
		Set<Class<?>> entities = scanForEntities("org.snomed.snowstorm.core.data.domain");
		entities.addAll(scanForEntities("org.snomed.snowstorm.fhir.domain"));
		// Remove Annotation class as it is not a persistent class but extends ReferenceSetMember
		entities.remove(Annotation.class);
		logger.debug("Found {} entities to initialise", entities.size());
		// Initialise Elasticsearch indices
		Map<String, Object> settings = new HashMap<>();
		settings.put("index.number_of_shards", indexShards);
		settings.put("index.number_of_replicas", indexReplicas);
		ComponentService.initialiseIndexAndMappingForPersistentClasses(deleteExisting, elasticsearchOperations, settings, entities.toArray(new Class<?>[]{}));
	}
}
