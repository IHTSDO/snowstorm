package org.snomed.snowstorm.config;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.google.common.base.Strings;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.aws.AWSRequestSigningApacheInterceptor;
import org.snomed.snowstorm.config.elasticsearch.DateToLongConverter;
import org.snomed.snowstorm.config.elasticsearch.LongToDateConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.support.HttpHeaders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ElasticsearchConfig extends ElasticsearchConfiguration {

	public static final String INDEX_MAX_TERMS_COUNT = "index.max_terms_count";

	@Value("${elasticsearch.username}")
	private String elasticsearchUsername;

	@Value("${elasticsearch.password}")
	private String elasticsearchPassword;

	@Value("${elasticsearch.index.prefix}")
	private String indexNamePrefix;

	@Value("${elasticsearch.index.shards}")
	private short indexShards;

	@Value("${elasticsearch.index.replicas}")
	private short indexReplicas;

	@Value("${snowstorm.aws.request-signing.enabled}")
	private Boolean awsRequestSigning;

	@Value("${elasticsearch.api-key}")
	private String apiKey;

	private final Logger logger = LoggerFactory.getLogger(getClass());


	@Override
	public @NotNull ClientConfiguration clientConfiguration() {
		final String[] urls = elasticsearchProperties().getUrls();
		for (String url : urls) {
			logger.info("Elasticsearch host: {}", url);
		}
		logger.info("Elasticsearch index prefix: {}", indexNamePrefix);
		return ClientConfiguration.builder()
				.connectedTo(getHosts(urls))
				.withConnectTimeout(Duration.ofSeconds(5))
				.withSocketTimeout(Duration.ofSeconds(3))
				.withHeaders(() -> {
					HttpHeaders headers = new HttpHeaders();
					if (!Strings.isNullOrEmpty(apiKey)) {
						headers.add(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey);
					}
					return headers;
				}).withClientConfigurer(
						ElasticsearchClients.ElasticsearchRestClientConfigurationCallback.from(clientBuilder -> {
							clientBuilder.setRequestConfigCallback(builder -> {
								builder.setConnectionRequestTimeout(0); //Disable lease handling for the connection pool! See https://github.com/elastic/elasticsearch/issues/24069
								return builder;
							});
							if (!Strings.isNullOrEmpty(elasticsearchUsername)&& !Strings.isNullOrEmpty(elasticsearchPassword)) {
								final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
								credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));
								clientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
							}
							if (awsRequestSigning != null && awsRequestSigning) {
								clientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.addInterceptorLast(awsInterceptor("es")));
							}
							return clientBuilder;
						})
				).build();
	}


	private AWSRequestSigningApacheInterceptor awsInterceptor(String serviceName) {
		AWS4Signer signer = new AWS4Signer();
		DefaultAwsRegionProviderChain regionProviderChain = new DefaultAwsRegionProviderChain();
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		signer.setServiceName(serviceName);
		signer.setRegionName(regionProviderChain.getRegion());

		return new AWSRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider);
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
}
