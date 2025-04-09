package org.snomed.snowstorm.syndication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

import static org.snomed.snowstorm.SnowstormApplication.getOneValueOrDefault;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.EXTENSION_COUNTRY_CODE;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.IMPORT_LOINC_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.LATEST_VERSION;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.SUPPORTED_TERMINOLOGIES;

@Service
public class StartupSyndicationService {

    @Autowired
    private Map<String, SyndicationService> syndicationServices;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void handleStartupSyndication(ApplicationArguments applicationArguments) throws IOException, ServiceException, InterruptedException {
        boolean isLoincIncluded = applicationArguments.containsOption(IMPORT_LOINC_TERMINOLOGY);
        String extensionName = getOneValueOrDefault(applicationArguments, EXTENSION_COUNTRY_CODE, null);
        for (String terminology : SUPPORTED_TERMINOLOGIES) {
            if (applicationArguments.containsOption(terminology)) {
                String version = getOneValueOrDefault(applicationArguments, terminology, LATEST_VERSION);
                SyndicationService service = syndicationServices.get(terminology);
                if (service == null) {
                    throw new IllegalStateException("No service found for terminology: " + terminology);
                }
                logger.info("Triggering import for: {} with version: {}", terminology, version);
                service.fetchAndImportTerminology(new SyndicationImportParams(version, extensionName, isLoincIncluded));
            }
        }
    }
}
