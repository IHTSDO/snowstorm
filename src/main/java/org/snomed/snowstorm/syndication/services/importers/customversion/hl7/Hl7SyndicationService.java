package org.snomed.snowstorm.syndication.services.importers.customversion.hl7;

import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRLoadPackageService;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.snomed.snowstorm.syndication.utils.FileUtils.findFile;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.HL_7_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.utils.CommandUtils.getSingleLineCommandResult;
import static org.snomed.snowstorm.syndication.utils.CommandUtils.waitForProcessTermination;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LATEST_VERSION;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOCAL_VERSION;

@Service(HL_7_TERMINOLOGY)
public class Hl7SyndicationService extends SyndicationService {

    @Autowired
    private FHIRLoadPackageService loadPackageService;

    @Autowired
    private FHIRCodeSystemService fhirCodeSystemService;

    @Value("${syndication.hl7.working-directory}")
    private String workingDirectory;

    @Value("${syndication.hl7.fileNamePattern}")
    private String fileNamePattern;

    @Value("${syndication.hl7.fhir.version}")
    private String fhirVersion;

    @Override
    protected List<File> fetchTerminologyPackages(SyndicationImportParams params) throws IOException, InterruptedException, ServiceException {
        Optional<File> file = LOCAL_VERSION.equals(params.version())
                ? findFile(workingDirectory, fileNamePattern)
                : downloadHl7File(params.version());
        return singletonList(file.orElseThrow(() -> new ServiceException("Hl7 terminology file not found, cannot be imported")));
    }

    /**
     * Will import the hl7 terminology. If a hl7 terminology file is already present on the filesystem, it will use it.
     * Else, it will download the latest version or version @param version if specified.
     * If the LOINC terminology is supposed to be used as well, a conflicting codeSystem imported through hl7 must be removed ( see <a href="https://github.com/IHTSDO/snowstorm/issues/609"></a>)
     */
    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws IOException {
        importHl7Package(files.get(0));
        if (params.isLoincImportIncluded()) {
            deleteConflictingHl7CodeSystem();
        }
    }

    private Optional<File> downloadHl7File(String version) throws IOException, InterruptedException {
        String versionSuffix = LATEST_VERSION.equals(version) ? "" : "@" + version;
        String packageName = "hl7.terminology." + fhirVersion + versionSuffix;
        Process process = new ProcessBuilder("npm", "--registry", "https://packages.simplifier.net", "pack", packageName)
                .directory(new File(workingDirectory))
                .start();
        waitForProcessTermination(process, "Download hl7 terminology");
        return findFile(workingDirectory, fileNamePattern);
    }

    private void importHl7Package(File file) throws IOException {
        String fileName = file.getName();
        logger.info("Importing HL7 Terminology from: {}", fileName);
        loadPackageService.uploadPackageResources(file, Set.of("*"), fileName, false);
    }

    private void deleteConflictingHl7CodeSystem() {
        FHIRCodeSystemVersionParams codeSystemVersionParams = new FHIRCodeSystemVersionParams("http://loinc.org");
        codeSystemVersionParams.setId("v3-loinc");
        FHIRCodeSystemVersion codeSystemVersion = fhirCodeSystemService.findCodeSystemVersion(codeSystemVersionParams);
        if (codeSystemVersion != null) {
            fhirCodeSystemService.deleteCodeSystemVersion(codeSystemVersion);
        }
    }

    @Override
    protected String getTerminologyVersion(String releaseFileName) {
        return releaseFileName.replaceAll("^hl7\\.terminology\\.r4-(\\d+\\.\\d+\\.\\d+)(?:-[^.]+)?\\.tgz$", "$1");
    }

    @Override
    protected String getLatestTerminologyVersion(String params) throws IOException, InterruptedException {
        return getSingleLineCommandResult("curl -s https://packages.simplifier.net/hl7.terminology.r4 | jq -r '.versions | map(.version)[]' | tail -n 1");
    }
}
