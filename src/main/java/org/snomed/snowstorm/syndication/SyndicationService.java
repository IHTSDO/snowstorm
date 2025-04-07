package org.snomed.snowstorm.syndication;

import org.slf4j.Logger;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.common.SyndicationImportStatusService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.snomed.snowstorm.syndication.data.SyndicationImportStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.apache.logging.log4j.util.Strings.isNotBlank;
import static org.snomed.snowstorm.core.util.FileUtils.removeDownloadedFiles;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.LATEST_VERSION;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.LOCAL_VERSION;

@Service
public abstract class SyndicationService {

    @Autowired
    protected SyndicationImportStatusService importStatusService;

    protected final String terminologyName;
    protected final Logger logger;

    protected SyndicationService(String terminologyName, Logger logger) {
        this.terminologyName = terminologyName;
        this.logger = logger;
    }

    public void importTerminologyAndStoreResult(SyndicationImportParams params) throws IOException, ServiceException, InterruptedException {
        SyndicationImportStatus status = importStatusService.getImportStatus(terminologyName);
        if (alreadyImported(params, status)) {
            logger.info("Terminology {} version {} is already imported", terminologyName, status.getActualVersion());
            return;
        }
        String importedTerminologyId = null;
        try {
            List<File> files = fetchTerminologyPackages(params);
            if(CollectionUtils.isEmpty(files)) {
                throw new ServiceException("No terminology packages found for imported version" + status.getActualVersion());
            }
            importTerminology(params, files);
            importedTerminologyId = LOCAL_VERSION.equals(params.getVersion())
                    ? getLocalPackageIdentifier(files)
                    : getTerminologyVersion(files.get(files.size() - 1).getName());
            importStatusService.saveOrUpdateImportStatus(terminologyName, params.getVersion(), importedTerminologyId, true, null);
            if(!LOCAL_VERSION.equals(params.getVersion())) {
                removeDownloadedFiles(files);
            }
        } catch (Exception e) {
            importStatusService.saveOrUpdateImportStatus(terminologyName, params.getVersion(), importedTerminologyId, false, e.getMessage());
            throw e;
        }
    }


    private boolean alreadyImported(SyndicationImportParams params, SyndicationImportStatus status) throws ServiceException, IOException, InterruptedException {
        if (status != null && status.isSuccess() && isNotBlank(status.getActualVersion())) {
            if (status.getActualVersion().equals(params.getVersion())) {
                return true;
            }
            if(LOCAL_VERSION.equals(params.getVersion())) {
                String localPackageIdentifier = getLocalPackageIdentifier(fetchTerminologyPackages(params));
                return status.getActualVersion().equals(localPackageIdentifier);
            }
            if(LATEST_VERSION.equals(params.getVersion())) {
                logger.info("Fetching latest version number for terminology: {}", terminologyName);
                try {
                    String latestVersion = getLatestTerminologyVersion();
                    logger.info("Latest terminology version: {}", latestVersion);
                    return status.getActualVersion().equals(latestVersion);
                }
                catch (Exception e) {
                    logger.error("Failed to fetch latest version number for terminology: {}", terminologyName, e);
                }
            }
        }
        return false;
    }

    protected String getLocalPackageIdentifier(List<File> localPackages) {
        if(CollectionUtils.isEmpty(localPackages)) {
            return null;
        }
        File file = localPackages.get(localPackages.size() - 1);
        return file.getName() + "-" + file.length() + "-" + file.lastModified();
    }

    protected abstract List<File> fetchTerminologyPackages(SyndicationImportParams params) throws IOException, InterruptedException, ServiceException;

    protected abstract void importTerminology(SyndicationImportParams params, List<File> files) throws IOException, ServiceException, InterruptedException;

    protected abstract String getLatestTerminologyVersion() throws IOException, InterruptedException;

    protected abstract String getTerminologyVersion(String releaseFileName) throws IOException;
}
