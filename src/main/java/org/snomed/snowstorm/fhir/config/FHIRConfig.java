package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FHIRConfig {

	@Bean
	public FhirContext fhirContext() {
		return FhirContext.forR4();
	}


	@Bean
	public JenaInitialiser jenaInitialiser() {
		return new JenaInitialiser();
	}

	@Bean
	public FilterRegistrationBean<ForceBrowserJsonFilter> forceJsonFilter() {
		FilterRegistrationBean<ForceBrowserJsonFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new ForceBrowserJsonFilter());
		registrationBean.addUrlPatterns("/fhir/*");
		return registrationBean;
	}

	@Bean
	@ConfigurationProperties(prefix = "fhir.conceptmap")
	public FHIRConceptMapImplicitConfig getFhirConceptMapImplicitConfig() {
		return new FHIRConceptMapImplicitConfig();
	}


}
