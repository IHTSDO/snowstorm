package org.snomed.snowstorm.config;

import org.snomed.snowstorm.rest.converter.ItemsPageCSVConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		// Workaround until we have removed trailing slashes in UI
		configurer.setUseTrailingSlashMatch(true);
	}
	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		converters.add(new ItemsPageCSVConverter());
	}
}
