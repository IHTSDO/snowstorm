package org.ihtsdo.elasticsnomed.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.vanroy.springdata.jest.JestElasticsearchTemplate;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.repositories.config.BranchStoreMixIn;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriRewriteFilter;
import io.searchbox.client.JestClient;

import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.ihtsdo.elasticsnomed.core.data.domain.classification.Classification;
import org.ihtsdo.elasticsnomed.core.data.repositories.config.ConceptStoreMixIn;
import org.ihtsdo.elasticsnomed.core.data.repositories.config.DescriptionStoreMixIn;
import org.ihtsdo.elasticsnomed.core.data.repositories.config.RelationshipStoreMixIn;
import org.ihtsdo.elasticsnomed.core.data.services.FastJestResultsMapper;
import org.ihtsdo.elasticsnomed.core.data.services.ReferenceSetTypesConfigurationService;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.IdentifierCacheManager;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.IdentifierSource;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.LocalIdentifierSource;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.cis.CISClient;
import org.ihtsdo.elasticsnomed.core.rf2.rf2import.ImportService;
import org.ihtsdo.elasticsnomed.rest.config.BranchMixIn;
import org.ihtsdo.elasticsnomed.rest.config.ClassificationMixIn;
import org.ihtsdo.elasticsnomed.rest.security.RequestHeaderAuthenticationDecoratorWithRequiredRole;
import org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.aws.autoconfigure.context.ContextStackAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.EntityMapper;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.util.UrlPathHelper;

import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Predicates.not;
import static springfox.documentation.builders.PathSelectors.regex;

@SpringBootApplication(
		exclude = {ElasticsearchAutoConfiguration.class,
				ElasticsearchDataAutoConfiguration.class,
				ContextStackAutoConfiguration.class})
@EnableElasticsearchRepositories(
		basePackages = {
				"org.ihtsdo.elasticsnomed.core.data.repositories",
				"io.kaicode.elasticvc.repositories"
		})
@EnableConfigurationProperties
@EnableAsync
public abstract class Config {

	@Autowired
	private JestClient jestClient;
	
	public static final PageRequest PAGE_OF_ONE = new PageRequest(0, 1);

	@Bean
	public ExecutorService taskExecutor() {
		return Executors.newCachedThreadPool();
	}

	@Bean
	public JestElasticsearchTemplate elasticsearchTemplate() throws UnknownHostException {
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

		SimpleElasticsearchMappingContext mappingContext = new SimpleElasticsearchMappingContext();
		FastJestResultsMapper fastJestResultsMapper = new FastJestResultsMapper(mappingContext, entityMapper);
		return new JestElasticsearchTemplate(
				jestClient,
				new MappingElasticsearchConverter(mappingContext),
				fastJestResultsMapper
		);
	}

	@Bean
	public ObjectMapper getGeneralMapper() {
		return Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.mixIn(Branch.class, BranchMixIn.class)
				.mixIn(Classification.class, ClassificationMixIn.class)
				.build();
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
	public Docket api() {
		return new Docket(DocumentationType.SWAGGER_2)
				.select()
				.apis(RequestHandlerSelectors.any())
				.paths(not(regex("/error")))
				.build();
	}

	@Bean // Serialize message content to json using TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}

}
