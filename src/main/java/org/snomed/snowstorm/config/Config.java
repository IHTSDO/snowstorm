package org.snomed.snowstorm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.client.Request;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.classification.BranchClassificationStatusService;
import org.snomed.snowstorm.core.data.services.identifier.*;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.data.services.servicehook.CommitServiceHookClient;
import org.snomed.snowstorm.core.data.services.traceability.TraceabilityLogService;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.ecl.SECLObjectFactory;
import org.snomed.snowstorm.ecl.validation.ECLPreprocessingService;
import org.snomed.snowstorm.fhir.config.FHIRConceptMapImplicitConfig;
import org.snomed.snowstorm.mrcm.MRCMLoader;
import org.snomed.snowstorm.mrcm.MRCMUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.jdbc.DataSourceHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.scheduling.annotation.EnableAsync;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Long.parseLong;

@SpringBootApplication(
		exclude = {
				ElasticsearchDataAutoConfiguration.class,
				ElasticsearchRestClientAutoConfiguration.class,
				FlywayAutoConfiguration.class,
				HibernateJpaAutoConfiguration.class,
				DataSourceHealthContributorAutoConfiguration.class,
				DataSourceAutoConfiguration.class
		}
)
@EnableElasticsearchRepositories(
		basePackages = {
				"org.snomed.snowstorm.core.data.repositories",
				"io.kaicode.elasticvc.repositories",
				"org.snomed.snowstorm.fhir.repositories"
		})
@EnableConfigurationProperties
@PropertySource(value = "classpath:application.properties", encoding = "UTF-8")
@EnableAsync
public abstract class Config extends ElasticsearchConfig {

	public static final String DEFAULT_LANGUAGE_CODE = "en";
	public static final List<String> DEFAULT_LANGUAGE_CODES = Collections.singletonList(DEFAULT_LANGUAGE_CODE);

	public static final String DEFAULT_ACCEPT_LANG_HEADER = "en-X-" + Concepts.US_EN_LANG_REFSET + ",en-X-" + Concepts.GB_EN_LANG_REFSET + ",en";
	public static final List<LanguageDialect> DEFAULT_LANGUAGE_DIALECTS = List.of(
			new LanguageDialect("en", parseLong(Concepts.US_EN_LANG_REFSET)),
			new LanguageDialect("en", parseLong(Concepts.GB_EN_LANG_REFSET)),
			new LanguageDialect("en", null)
	);

	public static final String SYSTEM_USERNAME = "System";

	public static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);
	public static final int BATCH_SAVE_SIZE = 10000;

	// Branch metadata values
	public static final String DEFAULT_MODULE_ID_KEY = "defaultModuleId";
	public static final String DEFAULT_NAMESPACE_KEY = "defaultNamespace";
	public static final String EXPECTED_EXTENSION_MODULES = "expectedExtensionModules";
	public static final String DEPENDENCY_PACKAGE = "dependencyPackage";
	public static final String REQUIRED_LANGUAGE_REFSETS = "requiredLanguageRefsets";

	@Value("${elasticsearch.index.max.terms.count}")
	private int indexMaxTermsCount;

	@Value("${search.term.minimumLength}")
	private int searchTermMinimumLength;

	@Value("${search.term.maximumLength}")
	private int searchTermMaximumLength;

	@Autowired
	private Environment env;

	@Autowired
	private DomainEntityConfiguration domainEntityConfiguration;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptDefinitionStatusUpdateService conceptDefinitionStatusUpdateService;

	@Autowired
	private SemanticIndexUpdateService semanticIndexUpdateService;

	@Autowired
	private MRCMUpdateService mrcmUpdateService;

	@Autowired
	private BranchClassificationStatusService branchClassificationStatusService;

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private IntegrityService integrityService;
	
	@Autowired
	private MultiSearchService multiSearchService;

	@Autowired
	private CommitServiceHookClient commitServiceHookClient;

	@Autowired
	private MRCMLoader mrcmLoader;

	@Autowired
	private ECLPreprocessingService eclPreprocessingService;

	@Autowired
	private RefsetDescriptorUpdaterService refsetDescriptorUpdaterService;

	@Autowired
	private ReferencedConceptsLookupUpdateService refsetConceptsLookupUpdateService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void configureCommitListeners() {
		// Commit listeners will be called in this order
		branchService.addCommitListener(mrcmLoader);
		branchService.addCommitListener(conceptDefinitionStatusUpdateService);
		branchService.addCommitListener(semanticIndexUpdateService);
		branchService.addCommitListener(mrcmUpdateService);
		branchService.addCommitListener(branchClassificationStatusService);
		branchService.addCommitListener(refsetDescriptorUpdaterService);
		branchService.addCommitListener(integrityService);
		branchService.addCommitListener(refsetConceptsLookupUpdateService);
		branchService.addCommitListener(multiSearchService);
		branchService.addCommitListener(eclPreprocessingService);
		branchService.addCommitListener(commitServiceHookClient);
		branchService.addCommitListener(traceabilityLogService);
		branchService.addCommitListener(BranchMetadataHelper::clearTransientMetadata);
		branchService.addCommitListener(commit ->
			logger.info("Completed commit on {} in {} seconds.", commit.getBranch().getPath(), secondsDuration(commit.getTimepoint())));

		// Push configured term constraints into static field
		DescriptionCriteria.configure(searchTermMinimumLength, searchTermMaximumLength);
	}
	
	private String secondsDuration(Date timepoint) {
		return "" + (float) (new Date().getTime() - timepoint.getTime()) / 1000f;
	}

	@Bean
	public ExecutorService taskExecutor() {
		return Executors.newCachedThreadPool();
	}

	@Bean
	public BranchService getBranchService(@Autowired ObjectMapper objectMapper) {
		return new BranchService(objectMapper);
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
			@Value("${cis.timeout}") int timeoutSeconds,
			@Autowired ElasticsearchOperations elasticsearchOperations) {

		if (cisApiUrl.equals("local-random") || cisApiUrl.equals("local")) {// local is the legacy name
			return new LocalRandomIdentifierSource(elasticsearchOperations);
		} else if (cisApiUrl.equals("local-sequential")) {
			return new LocalSequentialIdentifierSource(elasticsearchOperations);
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
	public CodeSystemDefaultConfigurationService getCodeSystemConfigurationService() {
		return new CodeSystemDefaultConfigurationService();
	}
	
	@Bean
	@ConfigurationProperties(prefix = "fhir.conceptmap")
	public FHIRConceptMapImplicitConfig getFhirConceptMapImplicitConfig() {
		return new FHIRConceptMapImplicitConfig();
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
	@ConfigurationProperties(prefix = "uri.dereferencing")
	public WebRouterConfigurationService getWebRouterConfigurationService() {
		return new WebRouterConfigurationService();
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
	@Bean
	public ModelMapper modelMapper() {
		return new ModelMapper();
	}

	protected void updateIndexMaxTermsSettingForAllSnomedComponents() {
		for (Class<? extends SnomedComponent> componentClass : domainEntityConfiguration.getComponentTypeRepositoryMap().keySet()) {
			updateIndexMaxTermsSetting(componentClass);
		}
	}

	protected void updateIndexMaxTermsSetting(Class<?> domainEntityClass) {
		IndexOperations indexOperations = elasticsearchOperations.indexOps(elasticsearchOperations.getIndexCoordinatesFor(domainEntityClass));
		Integer existing = (Integer) indexOperations.getSettings().get(INDEX_MAX_TERMS_COUNT);
		if (existing == null || indexMaxTermsCount != existing) {
			// Update setting
			String indexName = elasticsearchOperations.getIndexCoordinatesFor(domainEntityClass).getIndexName();
			try {
				indexMaxTermsCount = indexMaxTermsCount <= 65536 ? 65536 : indexMaxTermsCount;
				Request updateSettingsRequest = new Request("PUT", "/" + indexName + "/_settings");
				updateSettingsRequest.setJsonEntity("{\"index.max_terms_count\": " + indexMaxTermsCount + "}");
				elasticsearchRestClient(clientConfiguration()).performRequest(updateSettingsRequest);
				logger.info("{} is updated to {} for {}", INDEX_MAX_TERMS_COUNT, indexMaxTermsCount, indexName);
			} catch (IOException e) {
				logger.error("Failed to update setting {} on index {}", INDEX_MAX_TERMS_COUNT, indexName, e);
			}
		}
	}


}
