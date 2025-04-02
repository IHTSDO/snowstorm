package org.snomed.snowstorm.config;

import com.google.common.base.Strings;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.repositories.config.IndexNameProvider;
import jakarta.annotation.PostConstruct;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
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
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.support.HttpHeaders;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import java.io.IOException;
import java.util.*;

public class ElasticsearchConfig extends ElasticsearchConfiguration {

	public static final String INDEX_MAX_TERMS_COUNT = "index.max_terms_count";
	public static final String INDEX_MAX_FIELDS_LIMIT = "index.mapping.total_fields.limit";

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
		HttpHeaders apiKeyHeaders = new HttpHeaders();
		if (!Strings.isNullOrEmpty(apiKey)) {
			logger.info("Using API key authentication.");
			apiKeyHeaders.add("Authorization", "ApiKey " + apiKey);
		}

		if (useHttps(urls)) {
			return ClientConfiguration.builder()
					.connectedTo(getHosts(urls))
					.usingSsl()
					.withDefaultHeaders(apiKeyHeaders)
					.withClientConfigurer(configureHttpClient())
					.build();
		} else {
			return ClientConfiguration.builder()
					.connectedTo(getHosts(urls))
					.withDefaultHeaders(apiKeyHeaders)
					.withClientConfigurer(configureHttpClient())
					.build();
		}
	}

	private boolean useHttps(String[] urls) {
		for (String url : urls) {
			if (url.startsWith("https://")) {
				return true;
			}
		}
		return false;
	}

	private ElasticsearchClients.ElasticsearchRestClientConfigurationCallback configureHttpClient() {
		return ElasticsearchClients.ElasticsearchRestClientConfigurationCallback.from(clientBuilder -> {
			clientBuilder.setRequestConfigCallback(builder -> {
				builder.setConnectionRequestTimeout(0);//Disable lease handling for the connection pool! See https://github.com/elastic/elasticsearch/issues/24069
				return builder;
			});
			final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			if (!Strings.isNullOrEmpty(elasticsearchUsername) && !Strings.isNullOrEmpty(elasticsearchPassword)) {
				credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));
			}
			clientBuilder.setHttpClientConfigCallback(httpClientBuilder -> {
				httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
				if (awsRequestSigning != null && awsRequestSigning) {
					httpClientBuilder.addInterceptorFirst(awsInterceptor("es"));
				}
				return httpClientBuilder;
			});
			return clientBuilder;
		});
	}


	private AwsRequestSigningApacheInterceptor awsInterceptor(String serviceName) {
		return new AwsRequestSigningApacheInterceptor(serviceName, Aws4Signer.create(), DefaultCredentialsProvider.create(), DefaultAwsRegionProviderChain.builder().build().getRegion());
	}

	private static String[] getHosts(String[] hosts) {
		List<HttpHost> httpHosts = new ArrayList<>();
		for (String host : hosts) {
			httpHosts.add(HttpHost.create(host));
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
