package org.snomed.snowstorm.fhir.config;

import org.snomed.snowstorm.fhir.services.FHIRMappingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FHIRConfig {
	
	@Bean
	public FHIRMappingService fhirMappingService() {
		return new FHIRMappingService().init();
	}

}
