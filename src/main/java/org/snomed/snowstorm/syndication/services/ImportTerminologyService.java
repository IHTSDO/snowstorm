package org.snomed.snowstorm.syndication.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.models.requestDto.SyndicationImportRequest;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.snomed.snowstorm.syndication.constants.SyndicationTerminology;
import org.snomed.snowstorm.syndication.models.data.SyndicationImport;
import org.snomed.snowstorm.syndication.services.importers.fixedversion.FixedVersionSyndicationService;
import org.snomed.snowstorm.syndication.services.importstatus.SyndicationImportStatusDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.apache.http.util.TextUtils.isBlank;
import static org.snomed.snowstorm.SnowstormApplication.getOneValueOrDefault;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.EXTENSION_COUNTRY_CODE;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LATEST_VERSION;

@Service
public class ImportTerminologyService {

    @Autowired
    private Map<String, SyndicationService> syndicationServices;

    @Autowired
    private SyndicationImportStatusDao syndicationImportStatusDao;

    @Autowired
    private ExecutorService executorService;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ApplicationArguments applicationArguments;

    public void handleStartupSyndication(ApplicationArguments appArguments) throws IOException, ServiceException, InterruptedException {
        applicationArguments = appArguments;
        importTerminologies();
    }

    @Scheduled(cron = "${SYNDICATION_CRON:-}")
    private void importTerminologies() throws ServiceException, IOException, InterruptedException {
        String extensionName = getOneValueOrDefault(applicationArguments, EXTENSION_COUNTRY_CODE, null);
        for (SyndicationTerminology terminology : SyndicationTerminology.values()) {
            if (terminology.importByDefault() || applicationArguments.containsOption(terminology.getName())) {
                String version = getOneValueOrDefault(applicationArguments, terminology.getName(), LATEST_VERSION);
                SyndicationService service = syndicationServices.get(terminology.getName());
                if (service == null) {
                    throw new IllegalStateException("No service found for terminology: " + terminology);
                }
                logger.info("Triggering import for: {} with version: {}", terminology, version);
                var params = new SyndicationImportParams(terminology, version, extensionName);
                SyndicationImport status = syndicationImportStatusDao.getImportStatus(params.terminology().getName());
                if (service.alreadyImported(params, status)) {
                    logger.info("Terminology {} version {} has already been imported", params.terminology().getName(), status.getActualVersion());
                } else {
                    service.fetchAndImportTerminology(params);
                }
            }
        }
    }

    /**
     * @return true if the terminology has already been imported, false otherwise
     */
    public boolean updateTerminology(SyndicationImportRequest request) throws ServiceException, IOException, InterruptedException {
        var params = new SyndicationImportParams(
                SyndicationTerminology.fromName(request.terminologyName()),
                isBlank(request.version()) ? LATEST_VERSION : request.version(),
                request.extensionName()
        );
        SyndicationImport status = syndicationImportStatusDao.getImportStatus(params.terminology().getName());
        SyndicationService service = syndicationServices.get(params.terminology().getName());
        if (!(service instanceof FixedVersionSyndicationService) && service.alreadyImported(params, status)) {
            return true;
        }
        logger.info("Update terminology using the following syndication parameters: {}", params);
        executorService.submit(() -> {
            try {
                syndicationServices.get(params.terminology().getName()).fetchAndImportTerminology(params);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return false;
    }
}
