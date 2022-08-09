package org.snomed.snowstorm.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriRewriteFilter;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.ihtsdo.drools.domain.Component;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.rest.ReadOnlyApi;
import org.snomed.snowstorm.rest.ReadOnlyApiWhenEnabled;
import org.snomed.snowstorm.rest.config.*;
import org.snomed.snowstorm.rest.pojo.BranchPojo;
import org.snomed.snowstorm.rest.security.AccessDeniedExceptionHandler;
import org.snomed.snowstorm.rest.security.RequiredRoleFilter;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityAndUriConfig {

	@Value("${snowstorm.rest-api.readonly}")
	private boolean restApiReadOnly;

	@Value("${snowstorm.rest-api.readonly.allowReadOnlyPostEndpoints}")
	private boolean restApiAllowReadOnlyPostEndpoints;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	@Value("${ims-security.required-role}")
	private String requiredRole;

	@Value("${json.serialization.indent_output}")
	private boolean jsonIndentOutput;

	@Autowired(required = false)
	private BuildProperties buildProperties;

	private final String[] excludedUrlPatterns = {
			"/version",
			"/swagger-ui/**",
			"/v3/api-docs/**"
	};

	@Bean
	public ObjectMapper getGeneralMapper() {
		Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.mixIn(Page.class, PageMixin.class)
				.mixIn(Branch.class, BranchMixIn.class)
				.mixIn(BranchPojo.class, BranchMixIn.class)
				.mixIn(Classification.class, ClassificationMixIn.class)
				.mixIn(CodeSystemVersion.class, CodeSystemVersionMixIn.class)
				.mixIn(InvalidContent.class, InvalidContentMixIn.class)
				.mixIn(Component.class, ComponentMixIn.class);

		if (jsonIndentOutput) {
			builder.featuresToEnable(SerializationFeature.INDENT_OUTPUT);
		}

		return builder.build();
	}

	@Bean
	public FilterRegistrationBean<BranchPathUriRewriteFilter> getUrlRewriteFilter() {
		// Encode branch paths in uri to allow request mapping to work
		return new FilterRegistrationBean<>(new BranchPathUriRewriteFilter(
				"/branches/(.*)/children",
				"/branches/(.*)/parents",
				"/branches/(.*)/metadata-upsert",
				"/branches/(.*)/actions/.*",
				"/branches/(.*)",
				"/rebuild/(.*)",
				"/browser/(.*)/validate/concept",
				"/browser/(.*)/validate/concepts",
				"/browser/(.*)/concepts.*",
				"/browser/(.*)/descriptions.*",
				"/browser/(.*)/members.*",
				"/(.*)/concepts",
				"/(.*)/concepts/.*",
				"/(.*)/relationships.*",
				"/(.*)/descriptions.*",
				"/(.*)/members.*",
				"/(.*)/expressions.*",
				"/(.*)/classifications.*",
				"/(.*)/integrity-check",
				"/(.*)/upgrade-integrity-check",
				"/(.*)/integrity-check-full",
				"/(.*)/report/.*",
				"/(.*)/authoring-stats.*",
				"/mrcm/(.*)/domain-attributes",
				"/mrcm/(.*)/attribute-values.*",
				"/mrcm/(.*)/concept-model-attribute-hierarchy",
				"/admin/(.*)/actions/.*",
				"/admin/permissions/(.*)/role/.*",
				"/admin/permissions/(.*)"
		));
	}

	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
		DefaultHttpFirewall firewall = new DefaultHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		return firewall;
	}

	@Bean
	public List<String> alwaysAllowReadOnlyPostEndpointPrefixes() {
		return Collections.singletonList("/fhir");
	}

	@Bean
	public List<String> alwaysAllowReadOnlyPostEndpoints() {
		return Lists.newArrayList("/util/ecl-model-to-string", "/util/ecl-string-to-model");
	}

	@Bean
	public List<String> whenEnabledAllowReadOnlyPostEndpoints() {
		return Collections.singletonList("/browser/{branch}/concepts/bulk-load");
	}

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.httpFirewall(allowUrlEncodedSlashHttpFirewall());
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf().disable();// lgtm [java/spring-disabled-csrf-protection]

		if (restApiReadOnly) {
			// Read-ony mode

			// Allow some explicitly defined endpoints
			ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry authorizeRequests = http.authorizeRequests();
			alwaysAllowReadOnlyPostEndpointPrefixes().forEach(prefix -> authorizeRequests.antMatchers(HttpMethod.POST, prefix + "/**").permitAll());
			alwaysAllowReadOnlyPostEndpoints().forEach(path -> authorizeRequests.antMatchers(HttpMethod.POST, path).permitAll());
			if (restApiAllowReadOnlyPostEndpoints) {
				whenEnabledAllowReadOnlyPostEndpoints().forEach(endpoint -> authorizeRequests.antMatchers(HttpMethod.POST, endpoint.replace("{branch}", "**")).permitAll());
			}

			// Block all other POST/PUT/PATCH/DELETE
			authorizeRequests
					.antMatchers(HttpMethod.POST, "/**").denyAll()
					.antMatchers(HttpMethod.PUT, "/**").denyAll()
					.antMatchers(HttpMethod.PATCH, "/**").denyAll()
					.antMatchers(HttpMethod.DELETE, "/**").denyAll()
					.anyRequest().permitAll();
		} else if (rolesEnabled) {
			http.addFilterBefore(new RequestHeaderAuthenticationDecorator(), FilterSecurityInterceptor.class);
			http.addFilterAt(new RequiredRoleFilter(requiredRole, excludedUrlPatterns), FilterSecurityInterceptor.class);

			http.authorizeRequests()
					.antMatchers(excludedUrlPatterns).permitAll()
					.anyRequest().authenticated()
					.and().exceptionHandling().accessDeniedHandler(new AccessDeniedExceptionHandler())
					.and().httpBasic();
		}
		return http.build();
	}

	@Bean
	public OpenAPI apiInfo() {
		final String version = buildProperties != null ? buildProperties.getVersion() : "DEV";
		return new OpenAPI()
				.info(new Info().title("Snowstorm")
						.description("SNOMED CT Terminology Server REST API")
						.version(version)
						.contact(new Contact().name("SNOMED International").url("https://www.snomed.org"))
						.license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0")))
				.externalDocs(new ExternalDocumentation().description("See more about Snowstorm in GitHub").url("https://github.com/IHTSDO/snowstorm"));
	}

	@Bean
	public GroupedOpenApi apiDocs() {
		GroupedOpenApi.Builder apiBuilder = GroupedOpenApi.builder()
				.group("snowstorm")
				.packagesToScan("org.snomed.snowstorm.rest");
		if (restApiReadOnly) {
			// Also hide endpoints related to authoring
			apiBuilder.pathsToExclude("/merge.*", "/review.*", ".*/classification.*", "/exports.*", "/imports.*");
			apiBuilder.addOpenApiMethodFilter(method -> (method.isAnnotationPresent(GetMapping.class)
					|| method.isAnnotationPresent(ReadOnlyApi.class)
					|| (restApiAllowReadOnlyPostEndpoints && method.isAnnotationPresent(ReadOnlyApiWhenEnabled.class))));
		}
		// Don't show the error or root endpoints in swagger
		apiBuilder.pathsToExclude("/error", "/");
		return apiBuilder.build();
	}

	@Bean
	public GroupedOpenApi springActuatorApi() {
		return GroupedOpenApi.builder().group("actuator")
				.packagesToScan("org.springframework.boot.actuate")
				.pathsToMatch("/actuator/**")
				.build();
	}

}
