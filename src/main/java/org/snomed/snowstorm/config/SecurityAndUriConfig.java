package org.snomed.snowstorm.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriRewriteFilter;
import org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.rest.config.BranchMixIn;
import org.snomed.snowstorm.rest.config.ClassificationMixIn;
import org.snomed.snowstorm.rest.config.CodeSystemVersionMixIn;
import org.snomed.snowstorm.rest.config.PageMixin;
import org.snomed.snowstorm.rest.pojo.BranchPojo;
import org.snomed.snowstorm.rest.security.RequestHeaderAuthenticationDecoratorWithRequiredRole;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.ApiSelectorBuilder;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Predicates.not;
import static springfox.documentation.builders.PathSelectors.regex;

@Configuration
@EnableWebSecurity
public class SecurityAndUriConfig extends WebSecurityConfigurerAdapter {

	@Value("${snowstorm.rest-api.readonly}")
	private boolean restApiReadOnly;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	@Bean
	public ObjectMapper getGeneralMapper() {
		return Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.mixIn(Page.class, PageMixin.class)
				.mixIn(Branch.class, BranchMixIn.class)
				.mixIn(BranchPojo.class, BranchMixIn.class)
				.mixIn(Classification.class, ClassificationMixIn.class)
				.mixIn(CodeSystemVersion.class, CodeSystemVersionMixIn.class)
				.build();
	}

	@Bean
	public FilterRegistrationBean getUrlRewriteFilter() {
		// Encode branch paths in uri to allow request mapping to work
		return new FilterRegistrationBean<>(new BranchPathUriRewriteFilter(
				"/branches/(.*)/children",
				"/branches/(.*)/parents",
				"/branches/(.*)/actions/.*",
				"/branches/(.*)",
				"/rebuild/(.*)",
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
				"/(.*)/integrity-check-full",
				"/(.*)/report/.*",
				"/mrcm/(.*)/domain-attributes",
				"/mrcm/(.*)/attribute-values.*",
				"/browser/(.*)/validate/concept",
				"/admin/(.*)/actions/.*"
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
	public List<String> allowReadOnlyPostEndpointPrefixes() {
		return Collections.singletonList("/fhir");
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.csrf().disable();

		if (restApiReadOnly) {
			// Read-ony mode

			// Allow some explicitly defined endpoints
			ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry authorizeRequests = http.authorizeRequests();
			allowReadOnlyPostEndpointPrefixes().forEach(prefix -> authorizeRequests.antMatchers(HttpMethod.POST, prefix + "/**").anonymous());

			// Block all other POST/PUT/PATCH/DELETE
			authorizeRequests
					.antMatchers(HttpMethod.POST, "/**").denyAll()
					.antMatchers(HttpMethod.PUT, "/**").denyAll()
					.antMatchers(HttpMethod.PATCH, "/**").denyAll()
					.antMatchers(HttpMethod.DELETE, "/**").denyAll()
					.anyRequest().anonymous();
		} else if (rolesEnabled) {
			http.authorizeRequests()
					.antMatchers("/admin/**").hasRole("snowstorm-admin")// ROLE_snowstorm-admin
					.antMatchers(HttpMethod.PUT, "/codesystems/**").hasRole("snowstorm-admin")
					.antMatchers(HttpMethod.POST, "/codesystems/**").hasRole("snowstorm-admin")
					.anyRequest().permitAll();
		}
	}

	@Bean
	// Swagger config
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

	@Bean
	public FilterRegistrationBean getSingleSignOnFilter() {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean<>(
				new RequestHeaderAuthenticationDecorator());
		filterRegistrationBean.setOrder(1);
		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean getRequiredRoleFilter(@Value("${ims-security.required-role}") String requiredRole) {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean<>(
				new RequestHeaderAuthenticationDecoratorWithRequiredRole(requiredRole)
						.addExcludedPath("/webjars/springfox-swagger-ui")
		);
		filterRegistrationBean.setOrder(2);
		return filterRegistrationBean;
	}

}
