package org.snomed.snowstorm.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriRewriteFilter;
import org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.rest.config.BranchMixIn;
import org.snomed.snowstorm.rest.config.ClassificationMixIn;
import org.snomed.snowstorm.rest.config.PageMixin;
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
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;

@Configuration
@EnableWebSecurity
public class SecurityAndUriConfig extends WebSecurityConfigurerAdapter {

	@Value("${snowstorm.rest-api.readonly}")
	private boolean restApiReadOnly;

	@Bean
	public ObjectMapper getGeneralMapper() {
		return Jackson2ObjectMapperBuilder
				.json()
				.defaultViewInclusion(false)
				.failOnUnknownProperties(false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.mixIn(Page.class, PageMixin.class)
				.mixIn(Branch.class, BranchMixIn.class)
				.mixIn(Classification.class, ClassificationMixIn.class)
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
				"/(.*)/concepts",
				"/(.*)/concepts/.*",
				"/(.*)/members.*",
				"/(.*)/classifications.*",
				"/mrcm/(.*)/domain-attributes",
				"/mrcm/(.*)/attribute-values.*",
				"/browser/(.*)/validate/concept"
		).rewriteEncodedSlash());
	}

	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
		DefaultHttpFirewall firewall = new DefaultHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		return firewall;
	}


	@Override
	public void configure(WebSecurity web) throws Exception {
		web.httpFirewall(allowUrlEncodedSlashHttpFirewall());
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.csrf().disable();

		if (restApiReadOnly) {
			// Read-ony mode. Block POST/PUT/PATCH/DELETE
			http.authorizeRequests()
					.mvcMatchers(HttpMethod.POST, "**").denyAll()
					.mvcMatchers(HttpMethod.PUT, "**").denyAll()
					.mvcMatchers(HttpMethod.PATCH, "**").denyAll()
					.mvcMatchers(HttpMethod.DELETE, "**").denyAll()
					.anyRequest().anonymous();
		}
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
