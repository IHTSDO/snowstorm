package org.snomed.snowstorm.syndication.services.importstatus;

import org.snomed.snowstorm.core.data.repositories.ImportStatusRepository;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.syndication.models.data.SyndicationImport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SyndicationImportStatusDao {

    @Autowired
    private ImportStatusRepository repository;

    public void saveOrUpdateImportStatus(String terminology, String requestedVersion, String actualVersion, ImportJob.ImportStatus status, String exception) {
        repository.save(new SyndicationImport(terminology, requestedVersion, actualVersion, status, exception));
    }

    public SyndicationImport getImportStatus(String terminology) {
        return repository.findById(terminology).orElse(null);
    }

    public List<SyndicationImport> getAllImportStatuses() {
        List<SyndicationImport> importStatus = new ArrayList<>();
        repository.findAll().forEach(importStatus::add);
        return importStatus;
    }
}
