package org.snomed.snowstorm.syndication.importstatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.syndication.data.SyndicationImport;
import org.snomed.snowstorm.core.data.repositories.ImportStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SyndicationImportService {

    @Autowired
    private ImportStatusRepository repository;

    private final Logger logger = LoggerFactory.getLogger(getClass());


    /**
     * Create or update an import status entry.
     */
    public void saveOrUpdateImportStatus(String terminology, String requestedVersion, String actualVersion, ImportJob.ImportStatus status, String exception) {
        logger.info("Saving syndication import status: terminology={}, requested version={}, actual version={}, status={}", terminology, requestedVersion, actualVersion, status);
        repository.save(new SyndicationImport(terminology, requestedVersion, actualVersion, status, exception));
    }

    /**
     * Read import status by terminology.
     */
    public SyndicationImport getImportStatus(String terminology) {
        return repository.findById(terminology).orElse(null);
    }

    /**
     * Read all import statuses.
     */
    public List<SyndicationImport> getAllImportStatuses() {
        List<SyndicationImport> importStatus = new ArrayList<>();
        repository.findAll().forEach(importStatus::add);
        return importStatus;
    }
}
