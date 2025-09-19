package org.snomed.snowstorm.syndication.services.importstatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.syndication.constants.SyndicationTerminology;
import org.snomed.snowstorm.syndication.models.data.SyndicationImport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyndicationImportStatusService {

    @Autowired
    private SyndicationImportStatusDao syndicationImportStatusDao;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Create or update an import status entry.
     */
    public void saveOrUpdateImportStatus(SyndicationTerminology terminology, String requestedVersion, String actualVersion, ImportJob.ImportStatus status, String exception) {
        logger.info("Saving syndication import status: terminology={}, requested version={}, actual version={}, status={}", terminology.getName(), requestedVersion, actualVersion, status);
        syndicationImportStatusDao.saveOrUpdateImportStatus(terminology.getName(), requestedVersion, actualVersion, status, exception);
    }

    public List<SyndicationImport> getAllImportStatuses(boolean runningOnly) {
        List<SyndicationImport> importStatuses = syndicationImportStatusDao.getAllImportStatuses();
        if(runningOnly) {
            return importStatuses.stream().filter(syndicationImport -> ImportJob.ImportStatus.RUNNING.equals(syndicationImport.getStatus())).toList();
        }
        return importStatuses;
    }

    public boolean isLoincPresent() {
        return syndicationImportStatusDao.getAllImportStatuses().stream().anyMatch(syndicationImport -> SyndicationTerminology.LOINC.getName().equals(syndicationImport.getTerminology()));
    }



    public boolean isImportRunning() {
        return !getAllImportStatuses(true).isEmpty();
    }
}
