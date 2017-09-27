package org.ihtsdo.elasticsnomed.config;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriRewriteFilter;
import org.ihtsdo.elasticsnomed.rest.security.RequestHeaderAuthenticationDecoratorWithRequiredRole;
import org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;

@Configuration
@EnableWebSecurity
public class SecurityAndUriConfig extends WebSecurityConfigurerAdapter {

	@Bean
	public FilterRegistrationBean getUrlRewriteFilter() {
		// Encode branch paths in uri to allow request mapping to work
		return new FilterRegistrationBean(new BranchPathUriRewriteFilter(
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
	}

	@Bean
	public FilterRegistrationBean getSingleSignOnFilter() {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(
				new RequestHeaderAuthenticationDecorator());
		filterRegistrationBean.setOrder(1);
		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean getRequiredRoleFilter(@Value("${ims-security.required-role}") String requiredRole) {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(
				new RequestHeaderAuthenticationDecoratorWithRequiredRole(requiredRole)
						.addExcludedPath("/webjars/springfox-swagger-ui")
		);
		filterRegistrationBean.setOrder(2);
		return filterRegistrationBean;
	}

}
