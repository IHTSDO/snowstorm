package org.snomed.snowstorm.config;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("snomed.release.storage")
public class SnomedReleaseResourceConfiguration extends ResourceConfiguration {
}
