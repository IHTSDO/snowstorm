package org.snomed.snowstorm.syndication;

import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.common.SyndicationImportStatusService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public abstract class SyndicationService {

    @Autowired
    protected SyndicationImportStatusService importStatusService;

    protected final String terminologyName;
    protected String actualVersion;

    protected SyndicationService(String terminologyName) {
        this.terminologyName = terminologyName;
    }

    protected void importTerminologyAndStoreResult(SyndicationImportParams params) throws IOException, ServiceException, InterruptedException {
        try {
            importTerminology(params);
            importStatusService.saveOrUpdateImportStatus(terminologyName, params.getVersion(), actualVersion, true, null);
        } catch (Exception e) {
            importStatusService.saveOrUpdateImportStatus(terminologyName, params.getVersion(), actualVersion, false, e.getMessage());
            throw e;
        }
    }

    protected abstract void importTerminology(SyndicationImportParams params) throws IOException, ServiceException, InterruptedException;

    protected abstract void setActualTerminologyVersion(String releaseFileName);
}
