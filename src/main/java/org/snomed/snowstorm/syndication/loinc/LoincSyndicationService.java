package org.snomed.snowstorm.syndication.loinc;

import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.SyndicationService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.apache.logging.log4j.util.Strings.isNotBlank;
import static org.snomed.snowstorm.core.util.FileUtils.findFileName;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.IMPORT_LOINC_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.common.SyndicationUtils.waitForProcessTermination;

@Service(IMPORT_LOINC_TERMINOLOGY)
public class LoincSyndicationService extends SyndicationService {

    @Value("${syndication.loinc.working-directory}")
    private String workingDirectory;

    @Value("${syndication.loinc.fileNamePattern}")
    private String fileNamePattern;

    public LoincSyndicationService() {
        super("Loinc");
    }

    /**
     * Will import the loinc terminology. If a loinc terminology file is already present on the filesystem, it will use it.
     * Else, it will download the latest version or version @param version if specified
     */
    @Override
    protected void importTerminology(SyndicationImportParams params) throws IOException, ServiceException, InterruptedException {
        Optional<String> fileName = findFileName(workingDirectory, fileNamePattern);
        if (fileName.isEmpty()) {
            fileName = downloadLoincZip(params.getVersion());
        }
        importLoincZip(fileName);
    }

    private Optional<String> downloadLoincZip(String version) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("node", "./download_loinc.mjs", version == null ? "" : version)
                .directory(new File(workingDirectory))
                .start();

        waitForProcessTermination(process, "Download LOINC terminology");
        return findFileName(workingDirectory, fileNamePattern);
    }

    private void importLoincZip(Optional<String> fileName) throws IOException, InterruptedException, ServiceException {
        if (fileName.isEmpty()) {
            throw new ServiceException("Unable to fetch the LOINC zip file");
        }
        setActualTerminologyVersion(fileName.get());
        Process process = new ProcessBuilder(
                "./hapi-fhir-cli", "upload-terminology",
                "-d", fileName.get(),
                "-v", "r4",
                "-t", "http://localhost:8080/fhir",
                "-u", "http://loinc.org")
                .directory(new File(workingDirectory))
                .start();

        waitForProcessTermination(process, "Import LOINC terminology");
    }

    @Override
    protected void setActualTerminologyVersion(String releaseFileName) {
        String version = releaseFileName.replaceAll("^Loinc_(\\d+\\.\\d+)\\.zip$", "$1");
        actualVersion = isNotBlank(version) ? version : releaseFileName;
    }
}
