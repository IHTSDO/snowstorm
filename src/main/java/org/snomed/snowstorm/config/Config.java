package org.snomed.snowstorm.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.repositories.config.BranchStoreMixIn;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.snowstorm.config.elasticsearch.IndexConfig;
import org.snomed.snowstorm.config.elasticsearch.SnowstormElasticsearchMappingContext;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.config.ConceptStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.DescriptionStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.RelationshipStoreMixIn;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierCacheManager;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierSource;
import org.snomed.snowstorm.core.data.services.identifier.LocalIdentifierSource;
import org.snomed.snowstorm.core.data.services.identifier.SnowstormCISClient;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.ecl.SECLObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.aws.autoconfigure.context.ContextStackAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.EntityMapper;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentEntity;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.elasticsearch.rest.ElasticsearchRestClient;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.ApiSelectorBuilder;
import springfox.documentation.spring.web.plugins.Docket;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Predicates.not;
import static springfox.documentation.builders.PathSelectors.regex;

@SpringBootApplication(
		exclude = {
				ElasticsearchAutoConfiguration.class,
				ElasticsearchDataAutoConfiguration.class,
				ContextStackAutoConfiguration.class,
		})
@EnableElasticsearchRepositories(
		basePackages = {
				"org.snomed.snowstorm.core.data.repositories",
				"io.kaicode.elasticvc.repositories"
		})
@EnableConfigurationProperties
@EnableAsync
public abstract class Config {

	public static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);
	public static final List<String> DEFAULT_LANGUAGE_CODES = Collections.singletonList("en");
	public static final String SYSTEM_USERNAME = "System";
	public static final int BATCH_SAVE_SIZE = 10000;

	@Value("${snowstorm.rest-api.readonly}")
	private boolean restApiReadOnly;

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

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptDefinitionStatusUpdateService conceptDefinitionStatusUpdateService;

	@Autowired
	private SemanticIndexUpdateService semanticIndexUpdateService;

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void configureCommitListeners() {
		// Commit listeners will be called in this order
		branchService.addCommitListener(conceptDefinitionStatusUpdateService);
		branchService.addCommitListener(semanticIndexUpdateService);
		branchService.addCommitListener(traceabilityLogService);
	}

	@Bean
	public ExecutorService taskExecutor() {
		return Executors.newCachedThreadPool();
	}

	@Bean
	public ElasticsearchRestClient elasticsearchClient() {
		RestClientBuilder restClientBuilder = RestClient.builder(getHttpHosts(elasticsearchProperties().getUrls()));
		restClientBuilder.setRequestConfigCallback(builder -> {
			builder.setConnectionRequestTimeout(0); //Disable lease handling for the connection pool! See https://github.com/elastic/elasticsearch/issues/24069
			return builder;
		});

		if (elasticsearchUsername != null && !elasticsearchUsername.isEmpty()) {
			final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));
			restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
		}

		return new ElasticsearchRestClient(new HashMap<>(), restClientBuilder);
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

		SimpleElasticsearchMappingContext mappingContext = new SnowstormElasticsearchMappingContext(new IndexConfig(indexNamePrefix, indexShards, indexReplicas));
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

	protected void initialiseIndices(boolean deleteExisting) {
		// Initialse Elasticsearch indices
		Class<?>[] allDomainEntityTypes = domainEntityConfiguration().getAllDomainEntityTypes().toArray(new Class<?>[]{});
		ComponentService.initialiseIndexAndMappingForPersistentClasses(
				deleteExisting,
				elasticsearchTemplate,
				allDomainEntityTypes
		);
		if (deleteExisting) {
			Set<Class> objectsNotVersionControlled = Sets.newHashSet(CodeSystem.class, CodeSystemVersion.class);
			for (Class aClass : objectsNotVersionControlled) {
				ElasticsearchPersistentEntity persistentEntity = elasticsearchTemplate.getPersistentEntityFor(aClass);
				logger.info("Deleting index {}", persistentEntity.getIndexName());
				elasticsearchTemplate.deleteIndex(persistentEntity.getIndexName());
				logger.info("Creating index {}", persistentEntity.getIndexName());
				elasticsearchTemplate.createIndex(aClass);
				elasticsearchTemplate.putMapping(aClass);
			}
		}
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
	public IdentifierSource getIdentifierStorage(
			@Value("${cis.api.url}") String cisApiUrl,
			@Value("${cis.username}") String username,
			@Value("${cis.password}") String password,
			@Value("${cis.softwareName}") String softwareName,
			@Value("${cis.timeout}") int timeoutSeconds) {

		if (cisApiUrl.equals("local")) {
			return new LocalIdentifierSource();
		} else {
			return new SnowstormCISClient(cisApiUrl, username, password, softwareName, timeoutSeconds);
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
	@ConfigurationProperties(prefix = "codesystem")
	public CodeSystemConfigurationService getCodeSystemConfigurationService() {
		return new CodeSystemConfigurationService();
	}

	@Bean
	public ECLQueryBuilder eclQueryBuilder() {
		return new ECLQueryBuilder(new SECLObjectFactory());
	}

	@Bean(name = "allowReadOnlyPostEndpointPrefixes")
	public List<String> allowReadOnlyPostEndpointPrefixes() {
		return Collections.singletonList("/fhir");
	}

	@Bean
	public Docket api() {
		Docket docket = new Docket(DocumentationType.SWAGGER_2);
		docket.apiInfo(new ApiInfo("Snowstorm", "SNOMED CT Terminology Server REST API", "1.0", null, new Contact("SNOMED International", "https://github.com/IHTSDO/snowstorm", null), "Apache 2.0", "http://www.apache.org/licenses/LICENSE-2.0"));
		ApiSelectorBuilder apiSelectorBuilder = docket.select();

		if (restApiReadOnly) {
			// Read-only mode
			List<String> allowReadOnlyPostEndpointPrefixes = allowReadOnlyPostEndpointPrefixes();

			apiSelectorBuilder
					.apis(requestHandler -> {
						// Hide POST/PUT/PATCH/DELETE
						if (requestHandler != null) {
							// Allow FHIR endpoints with GET method (even if endpoint has POST too)
							RequestMappingInfo requestMapping = requestHandler.getRequestMapping();
							if (requestMapping.getPatternsCondition().getPatterns().stream()
									.filter(pattern -> allowReadOnlyPostEndpointPrefixes.stream().filter(pattern::startsWith).count() > 0).count() > 0
									&& requestMapping.getMethodsCondition().getMethods().contains(RequestMethod.GET)) {
								return true;
							}
							Set<RequestMethod> methods = requestMapping.getMethodsCondition().getMethods();
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
