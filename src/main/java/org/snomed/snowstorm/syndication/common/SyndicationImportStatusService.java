package org.snomed.snowstorm.syndication.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.syndication.data.SyndicationImportStatus;
import org.snomed.snowstorm.core.data.repositories.ImportStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SyndicationImportStatusService {

    @Autowired
    private ImportStatusRepository repository;

    private final Logger logger = LoggerFactory.getLogger(getClass());


    /**
     * Create or update an import status entry.
     */
    public void saveOrUpdateImportStatus(String terminology, String requestedVersion, String actualVersion, boolean success, String exception) {
        logger.info("Saving import status: terminology={}, requested version={}, actual version={}, successful={}", terminology, requestedVersion, actualVersion, success);
        SyndicationImportStatus status = new SyndicationImportStatus(terminology, requestedVersion, actualVersion, success, exception);
        repository.save(status);
    }

    /**
     * Read import status by terminology.
     */
    public Optional<SyndicationImportStatus> getImportStatus(String terminology) {
        return repository.findById(terminology);
    }
}
