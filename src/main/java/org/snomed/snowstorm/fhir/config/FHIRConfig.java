package org.snomed.snowstorm.fhir.config;

import org.snomed.snowstorm.fhir.rest.HapiRestfulServlet;
import org.snomed.snowstorm.fhir.services.FHIRMappingService;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FHIRConfig {
	
	@Bean
	public FHIRMappingService fhirMappingService() {
		return new FHIRMappingService().init();
	}
	
	@Bean
	public ServletRegistrationBean<HapiRestfulServlet> hapi() {
		HapiRestfulServlet hapiServlet = new HapiRestfulServlet();
		
		ServletRegistrationBean<HapiRestfulServlet> servletRegistrationBean = new ServletRegistrationBean<>(hapiServlet, "/fhir/*");
		servletRegistrationBean.setName("Hapi FHIR");
		return servletRegistrationBean;
	}

}
