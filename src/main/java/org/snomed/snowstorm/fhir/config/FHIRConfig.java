package org.snomed.snowstorm.fhir.config;

import org.snomed.snowstorm.fhir.services.HapiCodeSystemMapper;
import org.snomed.snowstorm.fhir.services.HapiValueSetMapper;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FHIRConfig {
	
	@Bean
	public HapiCodeSystemMapper hapiCodeSystemMapper() {
		return new HapiCodeSystemMapper();
	}
	
	@Bean
	public HapiValueSetMapper hapiValueSetMapper() {
		return new HapiValueSetMapper();
	}
	
	@Bean
	public ServletRegistrationBean<HapiRestfulServlet> hapi() {
		HapiRestfulServlet hapiServlet = new HapiRestfulServlet();
		
		ServletRegistrationBean<HapiRestfulServlet> servletRegistrationBean = new ServletRegistrationBean<>(hapiServlet, "/fhir/*");
		//servletRegistrationBean.setName("Hapi FHIR");
		hapiServlet.setServerName("Snowstorm FHIR Server");
		hapiServlet.setServerVersion("0.0.1");
		return servletRegistrationBean;
	}

}
