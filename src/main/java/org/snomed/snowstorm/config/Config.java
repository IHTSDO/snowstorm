package org.snomed.snowstorm.config;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.http.AWSRequestSigningApacheInterceptor;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
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
import org.hl7.fhir.r4.model.ValueSet;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.snowstorm.config.elasticsearch.IndexConfig;
import org.snomed.snowstorm.config.elasticsearch.SnowstormElasticsearchMappingContext;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.core.data.domain.classification.EquivalentConcepts;
import org.snomed.snowstorm.core.data.domain.classification.RelationshipChange;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.data.domain.jobs.IdentifiersForRegistration;
import org.snomed.snowstorm.core.data.repositories.config.CodeSystemMixIn;
import org.snomed.snowstorm.core.data.repositories.config.ConceptStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.DescriptionStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.RelationshipStoreMixIn;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierCacheManager;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierSource;
import org.snomed.snowstorm.core.data.services.identifier.LocalRandomIdentifierSource;
import org.snomed.snowstorm.core.data.services.identifier.SnowstormCISClient;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.ecl.SECLObjectFactory;
import org.snomed.snowstorm.fhir.domain.ValueSetDeserializer;
import org.snomed.snowstorm.fhir.domain.ValueSetSerializer;
import org.snomed.snowstorm.mrcm.MRCMUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.aws.autoconfigure.context.ContextStackAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
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

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Long.parseLong;

@SpringBootApplication(
		exclude = {
				ElasticsearchAutoConfiguration.class,
				ElasticsearchDataAutoConfiguration.class,
				ContextStackAutoConfiguration.class,
		})
@EnableElasticsearchRepositories(
		basePackages = {
				"org.snomed.snowstorm.core.data.repositories",
				"io.kaicode.elasticvc.repositories",
				"org.snomed.snowstorm.fhir.repositories"
		})
@EnableConfigurationProperties
@PropertySource(value = "classpath:application.properties", encoding = "UTF-8")
@EnableAsync
public abstract class Config {

	public static final String DEFAULT_LANGUAGE_CODE = "en";
	public static final List<String> DEFAULT_LANGUAGE_CODES = Collections.singletonList(DEFAULT_LANGUAGE_CODE);

	public static final String DEFAULT_ACCEPT_LANG_HEADER = "en-X-" + Concepts.US_EN_LANG_REFSET + ",en-X-" + Concepts.GB_EN_LANG_REFSET + ",en";
	public static final List<LanguageDialect> DEFAULT_LANGUAGE_DIALECTS = Lists.newArrayList(
			new LanguageDialect("en", parseLong(Concepts.US_EN_LANG_REFSET)),
			new LanguageDialect("en", parseLong(Concepts.GB_EN_LANG_REFSET)),
			new LanguageDialect("en", null)
	);

	public static final String SYSTEM_USERNAME = "System";

	public static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);
	public static final int BATCH_SAVE_SIZE = 10000;
	public static final int AGGREGATION_SEARCH_SIZE = 200;

	// Branch metadata values
	public static final String DEFAULT_MODULE_ID_KEY = "defaultModuleId";
	public static final String DEFAULT_NAMESPACE_KEY = "defaultNamespace";

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

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptDefinitionStatusUpdateService conceptDefinitionStatusUpdateService;

	@Autowired
	private SemanticIndexUpdateService semanticIndexUpdateService;

	@Autowired
	private MRCMUpdateService mrcmUpdateService;

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;
	
	@Autowired
	private DialectConfigurationService dialectService;
	
	private Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void configureCommitListeners() {
		// Commit listeners will be called in this order
		branchService.addCommitListener(conceptDefinitionStatusUpdateService);
		branchService.addCommitListener(semanticIndexUpdateService);
		branchService.addCommitListener(mrcmUpdateService);
		branchService.addCommitListener(traceabilityLogService);
		branchService.addCommitListener(commit -> {
			logger.info("Completed commit on {} in {} seconds.", commit.getBranch().getPath(), secondsDuration(commit.getTimepoint()));
		});
	}
	
	@PostConstruct
	public void initialseDialectService() {
		dialectService.report();
	}

	private String secondsDuration(Date timepoint) {
		return "" + (float) (new Date().getTime() - timepoint.getTime()) / 1000f;
	}

	@Bean
	public ExecutorService taskExecutor() {
		return Executors.newCachedThreadPool();
	}

	@Bean
	public ModelMapper modelMapper() {
		return new ModelMapper();
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

		if (awsRequestSigning != null && awsRequestSigning) {
			restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.addInterceptorLast(awsInterceptor("es")));
		}

		return new ElasticsearchRestClient(new HashMap<>(), restClientBuilder);
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
	public ElasticsearchTemplate elasticsearchTemplate() {
		final ObjectMapper elasticSearchMapper = Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.mixIn(Branch.class, BranchStoreMixIn.class)
				.mixIn(CodeSystem.class, CodeSystemMixIn.class)
				.mixIn(Concept.class, ConceptStoreMixIn.class)
				.mixIn(Description.class, DescriptionStoreMixIn.class)
				.mixIn(Relationship.class, RelationshipStoreMixIn.class)
				.serializerByType(ValueSet.class, new ValueSetSerializer())
				.deserializerByType(ValueSet.class, new ValueSetDeserializer())
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
			Set<Class> objectsNotVersionControlled = Sets.newHashSet(
					CodeSystem.class,
					CodeSystemVersion.class,
					Classification.class,
					RelationshipChange.class,
					EquivalentConcepts.class,
					IdentifiersForRegistration.class,
					ExportConfiguration.class
			);
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
	public VersionControlHelper getVersionControlHelper() {
		return new VersionControlHelper();
	}

	@Bean
	public IdentifierSource getIdentifierStorage(
			@Value("${cis.api.url}") String cisApiUrl,
			@Value("${cis.username}") String username,
			@Value("${cis.password}") String password,
			@Value("${cis.softwareName}") String softwareName,
			@Value("${cis.timeout}") int timeoutSeconds) {

		if (cisApiUrl.equals("local-random") || cisApiUrl.equals("local")) {// local is the legacy name
			return new LocalRandomIdentifierSource(elasticsearchTemplate());
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
	@ConfigurationProperties(prefix = "search.dialect")
	public DialectConfigurationService getDialectConfigurationService() {
		return new DialectConfigurationService();
	}

	@Bean
	@ConfigurationProperties(prefix = "search.language")
	public SearchLanguagesConfiguration searchLanguagesConfiguration() {
		return new SearchLanguagesConfiguration();
	}

	@Bean
	@ConfigurationProperties(prefix = "sort-order")
	public SortOrderProperties sortOrderProperties() {
		return new SortOrderProperties();
	}

	@Bean
	public ECLQueryBuilder eclQueryBuilder() {
		return new ECLQueryBuilder(new SECLObjectFactory());
	}

	@Bean // Serialize message content to json using TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}

}
