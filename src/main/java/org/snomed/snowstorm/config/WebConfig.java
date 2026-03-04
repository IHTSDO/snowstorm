package org.snomed.snowstorm.config;

import org.snomed.snowstorm.rest.converter.ItemsPageCSVConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.snomed.snowstorm.rest.interceptor.RequestLoggingInterceptor;

import java.util.List;

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

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		// Workaround until we have removed trailing slashes in UI
		configurer.setUseTrailingSlashMatch(true);
	}
	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		converters.add(new ItemsPageCSVConverter());
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// Register request logging interceptor for admin endpoints
		registry.addInterceptor(new RequestLoggingInterceptor("Admin request"))
				.addPathPatterns("/admin/**");
	}
}
