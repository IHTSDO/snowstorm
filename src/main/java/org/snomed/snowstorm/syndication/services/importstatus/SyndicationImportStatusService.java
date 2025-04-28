package org.snomed.snowstorm.syndication.services.importstatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.models.requestDto.SyndicationImportRequest;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.snomed.snowstorm.syndication.constants.SyndicationTerminology;
import org.snomed.snowstorm.syndication.models.data.SyndicationImport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.apache.http.util.TextUtils.isBlank;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LATEST_VERSION;

@Service
public class SyndicationImportStatusService {

    @Autowired
    private SyndicationImportStatusDao syndicationImportStatusDao;

    @Autowired
    private Map<String, SyndicationService> syndicationServices;

    @Autowired
    private ExecutorService executorService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Read import status by terminology.
     */
    public SyndicationImport getImportStatus(SyndicationTerminology terminology) {
        return syndicationImportStatusDao.getImportStatus(terminology.getName());
    }

    /**
     * Create or update an import status entry.
     */
    public void saveOrUpdateImportStatus(SyndicationTerminology terminology, String requestedVersion, String actualVersion, ImportJob.ImportStatus status, String exception) {
        logger.info("Saving syndication import status: terminology={}, requested version={}, actual version={}, status={}", terminology.getName(), requestedVersion, actualVersion, status);
        syndicationImportStatusDao.saveOrUpdateImportStatus(terminology.getName(), requestedVersion, actualVersion, status, exception);
    }

    public List<SyndicationImport> getAllImportStatuses() {
        return syndicationImportStatusDao.getAllImportStatuses();
    }

    public boolean isLoincPresent() {
        return syndicationImportStatusDao.getAllImportStatuses().stream().anyMatch(syndicationImport -> SyndicationTerminology.LOINC.getName().equals(syndicationImport.getTerminology()));
    }

    /**
     * @return true if the terminology has already been imported, false otherwise
     */
    public boolean updateTerminology(SyndicationImportRequest request) throws ServiceException, IOException, InterruptedException {
        var params = new SyndicationImportParams(
                SyndicationTerminology.fromName(request.terminologyName()),
                isBlank(request.version()) ? LATEST_VERSION : request.version(),
                request.extensionName(),
                isLoincPresent()
        );
        SyndicationImport status = getImportStatus(params.terminology());
        SyndicationService service = syndicationServices.get(params.terminology().getName());
        if (service.alreadyImported(params, status)) {
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

    public boolean isImportRunning() {
        return getAllImportStatuses().stream().anyMatch(status -> ImportJob.ImportStatus.RUNNING.equals(status.getStatus()));
    }
}
