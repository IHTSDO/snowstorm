package org.snomed.snowstorm.fhir.config;

import org.snomed.snowstorm.fhir.services.HapiParametersMapper;
import org.snomed.snowstorm.fhir.services.HapiValueSetMapper;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.rest.api.EncodingEnum;

@Configuration
public class FHIRConfig {
	
	@Bean
	public HapiParametersMapper hapiParametersMapper() {
		return new HapiParametersMapper();
	}
	
	@Bean
	public HapiValueSetMapper hapiValueSetMapper() {
		return new HapiValueSetMapper();
	}
	
	@Bean
	public ServletRegistrationBean<HapiRestfulServlet> hapi() {
		HapiRestfulServlet hapiServlet = new HapiRestfulServlet();
		
		ServletRegistrationBean<HapiRestfulServlet> servletRegistrationBean = new ServletRegistrationBean<>(hapiServlet, "/fhir/*");
		hapiServlet.setServerName("Snowstorm FHIR Server");
		hapiServlet.setServerVersion(getClass().getPackage().getImplementationVersion());
		hapiServlet.setDefaultResponseEncoding(EncodingEnum.JSON);
		return servletRegistrationBean;
	}

}
