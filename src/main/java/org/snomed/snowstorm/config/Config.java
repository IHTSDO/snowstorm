package org.snomed.snowstorm.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicate;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.repositories.config.BranchStoreMixIn;
import org.snomed.snowstorm.config.elasticsearch.SnowstormElasticsearchMappingContext;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.repositories.config.ConceptStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.DescriptionStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.RelationshipStoreMixIn;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierCacheManager;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierSource;
import org.snomed.snowstorm.core.data.services.identifier.LocalIdentifierSource;
import org.snomed.snowstorm.core.data.services.identifier.cis.CISClient;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.ecl.SECLObjectFactory;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.EntityMapper;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.elasticsearch.rest.ElasticsearchRestClient;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.RequestMethod;
import springfox.documentation.RequestHandler;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.ApiSelectorBuilder;
import springfox.documentation.spring.web.plugins.Docket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Predicates.not;
import static springfox.documentation.builders.PathSelectors.regex;

@SpringBootApplication(
		exclude = {ElasticsearchAutoConfiguration.class,
				ElasticsearchDataAutoConfiguration.class})
@EnableElasticsearchRepositories(
		basePackages = {
				"org.snomed.snowstorm.core.data.repositories",
				"io.kaicode.elasticvc.repositories"
		})
@EnableConfigurationProperties
@EnableAsync
public abstract class Config {

	public static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);

	@Value("${snowstorm.rest-api.readonly}")
	private boolean restApiReadOnly;

	@Bean
	public ExecutorService taskExecutor() {
		return Executors.newCachedThreadPool();
	}

	@Bean
	public ElasticsearchRestClient elasticsearchClient() {
		return new ElasticsearchRestClient(new HashMap<>(), elasticsearchProperties().getUrls());
	}

	@Bean
	public ElasticsearchProperties elasticsearchProperties() {
		return new ElasticsearchProperties();
	}


	@Value("${elasticsearch.index.prefix}")
	private String indexNamePrefix;

	@Bean
	public ElasticsearchTemplate elasticsearchTemplate() {
		final ObjectMapper elasticSearchMapper = Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.mixIn(Branch.class, BranchStoreMixIn.class)
				.mixIn(Concept.class, ConceptStoreMixIn.class)
				.mixIn(Description.class, DescriptionStoreMixIn.class)
				.mixIn(Relationship.class, RelationshipStoreMixIn.class)
				.build();

		EntityMapper entityMapper = new EntityMapper() {
			@Override
			public String mapToString(Object o) throws IOException {
				return elasticSearchMapper.writeValueAsString(o);
			}

			@Override
			public <T> T mapToObject(String s, Class<T> aClass) throws IOException {
				return elasticSearchMapper.readValue(s, aClass);
			}
		};

		SimpleElasticsearchMappingContext mappingContext = new SnowstormElasticsearchMappingContext(indexNamePrefix);
		FastResultsMapper fastResultsMapper = new FastResultsMapper(mappingContext, entityMapper);

		return new ElasticsearchTemplate(
				elasticsearchClient(),
				new MappingElasticsearchConverter(mappingContext),
				fastResultsMapper
		);
	}

	@Bean
	public DomainEntityConfiguration domainEntityConfiguration() {
		return new DomainEntityConfiguration();
	}

	public void initialiseIndices(boolean deleteExisting) {
		// Initialse Elasticsearch indices
		Class<?>[] allDomainEntityTypes = domainEntityConfiguration().getAllDomainEntityTypes().toArray(new Class<?>[]{});
		ComponentService.initialiseIndexAndMappingForPersistentClasses(
				deleteExisting,
				elasticsearchTemplate(),
				allDomainEntityTypes
		);
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
	public ExpressionService getExpressionService() {
		return new ExpressionService();
	}

	@Bean
	public AuthoringMirrorService getAuthoringMirrorService() {
		return new AuthoringMirrorService();
	}
	
	@Bean
	public IdentifierSource getIdentifierStorage(@Value("${cis.api.url}") String cisApiUrl) {
		if (cisApiUrl.equals("local")) {
			return new LocalIdentifierSource();
		} else {
			return new CISClient();  
		}
	}
	
	@Bean 
	public IdentifierCacheManager getIdentifierCacheManager(@Value("${cis.cache.concept-prefetch-count}") int conceptIdPrefetchCount) {
		IdentifierCacheManager icm = new IdentifierCacheManager();
		// Concept
		icm.addCache(0, "00", conceptIdPrefetchCount);
		// Description
		icm.addCache(0, "01", conceptIdPrefetchCount * 2);
		// Relationship
		icm.addCache(0, "02", conceptIdPrefetchCount * 4);
		return icm;
	}

	@Bean
	public VersionControlHelper getVersionControlHelper() {
		return new VersionControlHelper();
	}

	@Bean
	@ConfigurationProperties(prefix = "refset")
	public ReferenceSetTypesConfigurationService getReferenceSetTypesService() {
		return new ReferenceSetTypesConfigurationService();
	}

	@Bean
	public ECLQueryBuilder eclQueryBuilder() {
		return new ECLQueryBuilder(new SECLObjectFactory());
	}

	@Bean
	public Docket api() {
		ApiSelectorBuilder apiSelectorBuilder = new Docket(DocumentationType.SWAGGER_2).select();

		if (restApiReadOnly) {
			// Read-only mode
			apiSelectorBuilder
					.apis(requestHandler -> {
						// Hide POST/PUT/PATCH/DELETE
						if (requestHandler != null) {
							Set<RequestMethod> methods = requestHandler.getRequestMapping().getMethodsCondition().getMethods();
							return !methods.contains(RequestMethod.POST) && !methods.contains(RequestMethod.PUT)
									&& !methods.contains(RequestMethod.PATCH) && !methods.contains(RequestMethod.DELETE);
						}
						return false;
					})
					// Also hide endpoints related to authoring
					.paths(not(regex("/merge.*")))
					.paths(not(regex("/review.*")))
					.paths(not(regex(".*/classification.*")))
					.paths(not(regex("/exports.*")))
					.paths(not(regex("/imports.*")));
		} else {
			// Not read-only mode, allow everything!
			apiSelectorBuilder
					.apis(RequestHandlerSelectors.any());
		}

		// Don't show the error or root endpoints in swagger
		apiSelectorBuilder
				.paths(not(regex("/error")))
				.paths(not(regex("/")));

		return apiSelectorBuilder.build();
	}

	@Bean // Serialize message content to json using TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}

}
