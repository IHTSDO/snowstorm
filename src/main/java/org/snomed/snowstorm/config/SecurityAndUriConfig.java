package org.snomed.snowstorm.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriRewriteFilter;
import org.ihtsdo.drools.domain.Component;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.rest.config.*;
import org.snomed.snowstorm.rest.pojo.BranchPojo;
import org.snomed.snowstorm.rest.security.RequestHeaderAuthenticationDecoratorWithRequiredRole;
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
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.web.bind.annotation.RequestMethod;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.ApiSelectorBuilder;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.snomed.snowstorm.core.util.PredicateUtil.not;
import static springfox.documentation.builders.PathSelectors.regex;

@Configuration
@EnableWebSecurity
public class SecurityAndUriConfig extends WebSecurityConfigurerAdapter {

	@Value("${snowstorm.rest-api.readonly}")
	private boolean restApiReadOnly;

	@Value("${snowstorm.rest-api.readonly.allowReadOnlyPostEndpoints}")
	private boolean restApiAllowReadOnlyPostEndpoints;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	@Value("${json.serialization.indent_output}")
	private boolean jsonIndentOutput;

	@Autowired(required = false)
	private BuildProperties buildProperties;

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


	@Override
	public void configure(WebSecurity web) {
		web.httpFirewall(allowUrlEncodedSlashHttpFirewall());
	}

	@Bean
	public List<String> alwaysAllowReadOnlyPostEndpointPrefixes() {
		return Collections.singletonList("/fhir");
	}

	@Bean
	public List<String> alwaysAllowReadOnlyPostEndpoints() {
		return Collections.singletonList("/util/ecl-model-to-string");
	}

	@Bean
	public List<String> whenEnabledAllowReadOnlyPostEndpoints() {
		return Collections.singletonList("/browser/{branch}/concepts/bulk-load");
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.csrf().disable();// lgtm [java/spring-disabled-csrf-protection]

		if (restApiReadOnly) {
			// Read-ony mode

			// Allow some explicitly defined endpoints
			ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry authorizeRequests = http.authorizeRequests();
			alwaysAllowReadOnlyPostEndpointPrefixes().forEach(prefix -> authorizeRequests.antMatchers(HttpMethod.POST, prefix + "/**").anonymous());
			alwaysAllowReadOnlyPostEndpoints().forEach(path -> authorizeRequests.antMatchers(HttpMethod.POST, path).anonymous());
			if (restApiAllowReadOnlyPostEndpoints) {
				whenEnabledAllowReadOnlyPostEndpoints().forEach(endpoint -> authorizeRequests.antMatchers(HttpMethod.POST, endpoint.replace("{branch}", "**")).anonymous());
			}

			// Block all other POST/PUT/PATCH/DELETE
			authorizeRequests
					.antMatchers(HttpMethod.POST, "/**").denyAll()
					.antMatchers(HttpMethod.PUT, "/**").denyAll()
					.antMatchers(HttpMethod.PATCH, "/**").denyAll()
					.antMatchers(HttpMethod.DELETE, "/**").denyAll()
					.anyRequest().anonymous();
		} else if (rolesEnabled) {
			http
					.authorizeRequests()
					.anyRequest().permitAll();
		}
	}



	@Bean
	// Swagger config
	public Docket api() {
		Docket docket = new Docket(DocumentationType.SWAGGER_2);
        final String version = buildProperties != null ? buildProperties.getVersion() : "DEV";
		docket.apiInfo(new ApiInfo("Snowstorm", "SNOMED CT Terminology Server REST API", version, null,
				"SNOMED International (\"https://github.com/IHTSDO/snowstorm\")", "Apache 2.0", "http://www.apache.org/licenses/LICENSE-2.0"));
		ApiSelectorBuilder apiSelectorBuilder = docket.select();

		if (restApiReadOnly) {
			// Read-only mode
			List<String> alwaysAllowReadOnlyPostEndpointPrefixes = alwaysAllowReadOnlyPostEndpointPrefixes();
			List<String> alwaysAllowReadOnlyPostEndpoints = alwaysAllowReadOnlyPostEndpoints();
			List<String> whenEnabledAllowReadOnlyPostEndpoints = whenEnabledAllowReadOnlyPostEndpoints();

			apiSelectorBuilder
					.apis(requestHandler -> {
						// Hide POST/PUT/PATCH/DELETE
						if (requestHandler != null) {
							// Allow FHIR endpoints with GET method (even if endpoint has POST too)
							if (requestHandler.getPatternsCondition().getPatterns()
									.stream().
											anyMatch(pattern -> alwaysAllowReadOnlyPostEndpointPrefixes
													.stream()
													.anyMatch(pattern.toString()::startsWith))
									&& requestHandler.supportedMethods().contains(RequestMethod.GET)) {
								return true;
							}
							if (requestMapping.getPatternsCondition().getPatterns()
									.stream().
											anyMatch(pattern -> alwaysAllowReadOnlyPostEndpoints
													.stream()
													.anyMatch(pattern::equals))) {
								return true;
							}
							if (restApiAllowReadOnlyPostEndpoints) {
								// Allow specific endpoints with POST method
								if (requestHandler.getPatternsCondition().getPatterns()
										.stream().
												anyMatch(pattern -> whenEnabledAllowReadOnlyPostEndpoints
														.stream()
														.anyMatch(pattern::equals))
										&& requestHandler.supportedMethods().contains(RequestMethod.POST)) {
									return true;
								}
							}
							Set<RequestMethod> methods = requestHandler.supportedMethods();
							return !methods.contains(RequestMethod.POST) && !methods.contains(RequestMethod.PUT)
									&& !methods.contains(RequestMethod.PATCH) && !methods.contains(RequestMethod.DELETE);
						}
						return false;
					})
					.paths(not(regex("/merge.*")))
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

	@Bean
	public FilterRegistrationBean<RequestHeaderAuthenticationDecorator> getSingleSignOnFilter() {
		FilterRegistrationBean<RequestHeaderAuthenticationDecorator> filterRegistrationBean = new FilterRegistrationBean<>(
				new RequestHeaderAuthenticationDecorator());
		filterRegistrationBean.setOrder(1);
		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean<RequestHeaderAuthenticationDecoratorWithRequiredRole> getRequiredRoleFilter(@Value("${ims-security.required-role}") String requiredRole) {
		FilterRegistrationBean<RequestHeaderAuthenticationDecoratorWithRequiredRole> filterRegistrationBean = new FilterRegistrationBean<>(
				new RequestHeaderAuthenticationDecoratorWithRequiredRole(requiredRole)
						.addExcludedPath("/webjars/springfox-swagger-ui")
		);
		filterRegistrationBean.setOrder(2);
		return filterRegistrationBean;
	}

}
