package org.snomed.snowstorm.config;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="module.storage")
public class ModuleStorageResourceConfig extends ResourceConfiguration {
	
}
