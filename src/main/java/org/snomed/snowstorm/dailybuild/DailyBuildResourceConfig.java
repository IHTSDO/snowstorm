package org.snomed.snowstorm.dailybuild;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("daily-build.import.resources")
public class DailyBuildResourceConfig extends ResourceConfiguration {

}
