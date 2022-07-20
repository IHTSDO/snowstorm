package org.snomed.snowstorm.config;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.aws.AWSRequestSigningApacheInterceptor;
import org.snomed.snowstorm.config.elasticsearch.DateToLongConverter;
import org.snomed.snowstorm.config.elasticsearch.IndexConfig;
import org.snomed.snowstorm.config.elasticsearch.LongToDateConverter;
import org.snomed.snowstorm.config.elasticsearch.SnowstormElasticsearchMappingContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ElasticsearchConfig {

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

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Bean
	public ModelMapper modelMapper() {
		return new ModelMapper();
	}

	@Bean
	public RestClients.ElasticsearchRestClient elasticsearchRestClient() {
		final String[] urls = elasticsearchProperties().getUrls();
		for (String url : urls) {
			logger.info("Elasticsearch host: {}", url);
		}
		logger.info("Elasticsearch index prefix: {}", indexNamePrefix);

		RestClientBuilder restClientBuilder = RestClient.builder(getHttpHosts(urls));
		restClientBuilder.setRequestConfigCallback(builder -> {
			builder.setConnectionRequestTimeout(0); //Disable lease handling for the connection pool! See https://github.com/elastic/elasticsearch/issues/24069
			return builder;
		});

		if (elasticsearchUsername != null && !elasticsearchUsername.isEmpty()) {
			final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));
			restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
		}

		if (awsRequestSigning != null && awsRequestSigning) {
			restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.addInterceptorLast(awsInterceptor("es")));
		}
		return () -> new RestHighLevelClient(restClientBuilder);
	}

	private AWSRequestSigningApacheInterceptor awsInterceptor(String serviceName) {
		AWS4Signer signer = new AWS4Signer();
		DefaultAwsRegionProviderChain regionProviderChain = new DefaultAwsRegionProviderChain();
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		signer.setServiceName(serviceName);
		signer.setRegionName(regionProviderChain.getRegion());

		return new AWSRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider);
	}

	private static HttpHost[] getHttpHosts(String[] hosts) {
		List<HttpHost> httpHosts = new ArrayList<>();
		for (String host : hosts) {
			httpHosts.add(HttpHost.create(host));
		}
		return httpHosts.toArray(new HttpHost[]{});
	}

	@Bean
	public ElasticsearchProperties elasticsearchProperties() {
		return new ElasticsearchProperties();
	}

	@Bean
	public ElasticsearchConverter elasticsearchConverter() {
		SimpleElasticsearchMappingContext mappingContext = new SnowstormElasticsearchMappingContext(new IndexConfig(indexNamePrefix, indexShards, indexReplicas));
		MappingElasticsearchConverter elasticsearchConverter = new MappingElasticsearchConverter(mappingContext);
		elasticsearchConverter.setConversions(elasticsearchCustomConversions());
		return elasticsearchConverter;
	}

	@Bean(name = { "elasticsearchOperations", "elasticsearchTemplate"})
	public ElasticsearchRestTemplate elasticsearchRestTemplate() {
		return new ElasticsearchRestTemplate(elasticsearchRestClient().rest(), elasticsearchConverter());
	}

	@Bean
	public ElasticsearchCustomConversions elasticsearchCustomConversions() {
		return new ElasticsearchCustomConversions(
				Arrays.asList(new DateToLongConverter(), new LongToDateConverter()));
	}

}
