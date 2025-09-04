package org.snomed.snowstorm.fhir.config;

import org.apache.jena.sys.JenaSystem;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JenaInitialiser {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		logger.info("Initialising Apache Jena - needed prior to first FHIR call");
		JenaSystem.init();
	}
}
