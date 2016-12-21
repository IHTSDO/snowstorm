package com.kaicube.snomed.elasticsnomed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.kaicube.elasticversioncontrol.api.BranchService;
import com.kaicube.elasticversioncontrol.api.VersionControlHelper;
import com.kaicube.snomed.elasticsnomed.domain.*;
import com.kaicube.snomed.elasticsnomed.repositories.config.ConceptStoreMixIn;
import com.kaicube.snomed.elasticsnomed.repositories.config.DescriptionStoreMixIn;
import com.kaicube.snomed.elasticsnomed.repositories.config.RelationshipStoreMixIn;
import com.kaicube.snomed.elasticsnomed.rest.BranchPathUrlRewriteFilter;
import com.kaicube.snomed.elasticsnomed.rf2import.ImportService;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.EntityMapper;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.google.common.base.Predicates.not;
import static springfox.documentation.builders.PathSelectors.regex;

@SpringBootApplication
@EnableElasticsearchRepositories(basePackages = {"com.kaicube.elasticversioncontrol.repositories", "com.kaicube.snomed.elasticsnomed.repositories"})
public class Config {

	@Bean
	public ElasticsearchTemplate elasticsearchTemplate() throws UnknownHostException {
		return getElasticsearchTemplate(elasticSearchClient());
	}

	@Bean // Use standalone Elasticsearch Server on localhost
	public Client elasticSearchClient() throws UnknownHostException {
		final InetAddress localhost = InetAddress.getLoopbackAddress();
		return TransportClient.builder().build()
				.addTransportAddress(new InetSocketTransportAddress(localhost, 9300));
	}

	@Bean
	public ObjectMapper getGeneralMapper() {
		return Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.build();
	}

	static ElasticsearchTemplate getElasticsearchTemplate(Client client) {
		final ObjectMapper elasticSearchMapper = Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.mixIn(Concept.class, ConceptStoreMixIn.class)
				.mixIn(Description.class, DescriptionStoreMixIn.class)
				.mixIn(Relationship.class, RelationshipStoreMixIn.class)
				.build();

		return new ElasticsearchTemplate(client, new EntityMapper() {
			@Override
			public String mapToString(Object o) throws IOException {
				return elasticSearchMapper.writeValueAsString(o);
			}
			@Override
			public <T> T mapToObject(String s, Class<T> aClass) throws IOException {
				return elasticSearchMapper.readValue(s, aClass);
			}
		});
	}

	@Bean
	public ConceptService getConceptService() {
		return new ConceptService();
	}

	@Bean
	public BranchService getBranchService() {
		return new BranchService();
	}

	@Bean
	public ImportService getImportService() {
		return new ImportService();
	}

	@Bean
	public VersionControlHelper getVersionControlHelper() {
		return new VersionControlHelper();
	}

	@Bean
	public FilterRegistrationBean getUrlRewriteFilter() {
		// Encode branch paths in uri to allow request mapping to work
		return new FilterRegistrationBean(new BranchPathUrlRewriteFilter(Lists.newArrayList(
				"/branches/(.*)/children",
				"/branches/(.*)",
				"/browser/(.*)/concepts",
				"/browser/(.*)/concepts",
				"/(.*)/descriptions"
		)));
	}

	@Bean
	public Docket api() {
		return new Docket(DocumentationType.SWAGGER_2)
				.select()
				.apis(RequestHandlerSelectors.any())
				.paths(not(regex("/error")))
				.build();
	}

}
