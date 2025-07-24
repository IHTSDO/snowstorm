package org.snomed.snowstorm.release;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("internal.release.storage")
public class InternalReleaseResourceConfig extends ResourceConfiguration {

}
