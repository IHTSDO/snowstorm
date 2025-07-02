package org.snomed.snowstorm.release;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("release.storage")
public class ReleaseResourceConfig extends ResourceConfiguration {

}
