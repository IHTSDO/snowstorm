package org.snomed.snowstorm.syndication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.snomed.snowstorm.syndication.common.SyndicationService;
import org.snomed.snowstorm.syndication.common.SyndicationTerminology;
import org.snomed.snowstorm.syndication.data.SyndicationImport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

import static org.snomed.snowstorm.SnowstormApplication.getOneValueOrDefault;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.EXTENSION_COUNTRY_CODE;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.LATEST_VERSION;
import static org.snomed.snowstorm.syndication.common.SyndicationTerminology.LOINC;

@Service
public class StartupSyndicationService {

    @Autowired
    private Map<String, SyndicationService> syndicationServices;

    @Autowired
    protected SyndicationImportService importStatusService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void handleStartupSyndication(ApplicationArguments applicationArguments) throws IOException, ServiceException, InterruptedException {
        boolean isLoincIncluded = applicationArguments.containsOption(LOINC.getImportArg());
        String extensionName = getOneValueOrDefault(applicationArguments, EXTENSION_COUNTRY_CODE, null);
        for (SyndicationTerminology terminology : SyndicationTerminology.values()) {
            if (terminology.importByDefault() || applicationArguments.containsOption(terminology.getImportArg())) {
                String version = getOneValueOrDefault(applicationArguments, terminology.getImportArg(), LATEST_VERSION);
                SyndicationService service = syndicationServices.get(terminology.getName());
                if (service == null) {
                    throw new IllegalStateException("No service found for terminology: " + terminology);
                }
                logger.info("Triggering import for: {} with version: {}", terminology, version);
                var params = new SyndicationImportParams(terminology, version, extensionName, isLoincIncluded);
                SyndicationImport status = importStatusService.getImportStatus(params.terminology());
                if (service.alreadyImported(params, status)) {
                    logger.info("Terminology {} version {} has already been imported", params.terminology().getName(), status.getActualVersion());
                } else {
                    service.fetchAndImportTerminology(params);
                }
            }
        }
    }
}
