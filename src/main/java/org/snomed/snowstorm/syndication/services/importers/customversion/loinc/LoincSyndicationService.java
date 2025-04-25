package org.snomed.snowstorm.syndication.services.importers.customversion.loinc;

import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.snomed.snowstorm.syndication.utils.FileUtils.findFile;
import static org.snomed.snowstorm.syndication.utils.CommandUtils.getSingleLineCommandResult;
import static org.snomed.snowstorm.syndication.utils.CommandUtils.waitForProcessTermination;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LATEST_VERSION;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOCAL_VERSION;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOINC_TERMINOLOGY;

@Service(LOINC_TERMINOLOGY)
public class LoincSyndicationService extends SyndicationService {

    @Value("${syndication.loinc.working-directory}")
    private String workingDirectory;

    @Value("${syndication.loinc.fileNamePattern}")
    private String fileNamePattern;

    @Override
    protected List<File> fetchTerminologyPackages(SyndicationImportParams params) throws IOException, InterruptedException, ServiceException {
        Optional<File> file = LOCAL_VERSION.equals(params.version())
                ? findFile(workingDirectory, fileNamePattern)
                : downloadLoincZip(params.version());
        return singletonList(file.orElseThrow(() -> new ServiceException("Loinc terminology file not found, cannot be imported")));
    }

    /**
     * Will import the loinc terminology. If a loinc terminology file is already present on the filesystem, it will use it.
     * Else, it will download the latest version or version @param version if specified
     */
    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws IOException, InterruptedException {
        String fileName = files.get(0).getName();
        Process process = new ProcessBuilder(
                "./hapi-fhir-cli", "upload-terminology",
                "-d", fileName,
                "-v", "r4",
                "-t", "http://localhost:8080/fhir",
                "-u", "http://loinc.org")
                .directory(new File(workingDirectory))
                .start();

        waitForProcessTermination(process, "Import LOINC terminology");
    }

    private Optional<File> downloadLoincZip(String version) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("node", "./download_loinc.mjs", LATEST_VERSION.equals(version) ? "" : version)
                .directory(new File(workingDirectory))
                .start();

        waitForProcessTermination(process, "Download LOINC terminology");
        return findFile(workingDirectory, fileNamePattern);
    }

    @Override
    protected String getTerminologyVersion(String releaseFileName) {
        return releaseFileName.replaceAll("^Loinc_(\\d+\\.\\d+)(?:-[^.]+)?\\.zip$", "$1");
    }

    @Override
    protected String getLatestTerminologyVersion(String params) throws IOException, InterruptedException {
        return getSingleLineCommandResult("curl -s https://loinc.org/downloads/ | grep -oP 'Loinc[_-]\\K[0-9]+\\.[0-9]+' | head -n 1");
    }
}
