package org.snomed.snowstorm.config;

import org.snomed.snowstorm.rest.converter.ItemsPageCSVConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.snomed.snowstorm.rest.interceptor.RequestLoggingInterceptor;


@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("${snowstorm.rest-api.allowAnyOrigin:true}")
	private boolean allowAnyOrigin;

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		if (allowAnyOrigin) {
			CorsConfiguration config = new CorsConfiguration();
			config.addAllowedOriginPattern("*");
			config.addAllowedMethod("*");
			config.addAllowedHeader("*");
			config.setAllowCredentials(false);
			source.registerCorsConfiguration("/**", config);
		}
		return source;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (allowAnyOrigin) {
			registry.addMapping("/**")
					.allowedOriginPatterns("*")
					.allowedMethods("*")
					.allowedHeaders("*")
					.allowCredentials(false);
		}
	}

	/*
	 * Replaces PathMatchConfigurer.setUseTrailingSlashMatch(true), deprecated in Spring Framework 6 and
	 * removed in 7. The UI still sends trailing slashes, so without this every such request 404s.
	 * wrapRequest() keeps the old semantics - the trailing slash is stripped from the path the rest of the
	 * chain sees, so no redirect is issued and clients see no behaviour change. It runs first so that the
	 * branch path rewrite filter and the security chain both see the normalised path.
	 */
	@Bean
	public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
		FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(
				UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

	/*
	 * The List taking overloads of this method both come with a catch: configureMessageConverters(List)
	 * registers the defaults only when nothing is added, so adding one converter there leaves nothing able
	 * to write JSON, and extendMessageConverters(List) - which appends to the defaults - is deprecated for
	 * removal in Spring Framework 7. This builder overload keeps the defaults and adds to them by contract.
	 */
	@Override
	public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
		builder.addCustomConverter(new ItemsPageCSVConverter());
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// Register request logging interceptor for admin endpoints
		registry.addInterceptor(new RequestLoggingInterceptor("Admin request"))
				.addPathPatterns("/admin/**");
	}
}
