package org.snomed.snowstorm.extension;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("versioned-content.resources")
public class VersionedContentResourceConfig extends ResourceConfiguration {
}
