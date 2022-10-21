package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FHIRConfig {

	@Bean
	public FhirContext fhirContext() {
		return FhirContext.forR4();
	}

}
