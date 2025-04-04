package org.snomed.snowstorm.syndication.hl7;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRLoadPackageService;
import org.snomed.snowstorm.syndication.SyndicationService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static org.apache.logging.log4j.util.Strings.isNotBlank;
import static org.snomed.snowstorm.core.util.FileUtils.findFile;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.IMPORT_HL_7_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.common.SyndicationUtils.waitForProcessTermination;

@Service(IMPORT_HL_7_TERMINOLOGY)
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

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public Hl7SyndicationService() {
        super("Hl7");
    }

    /**
     * Will import the hl7 terminology. If a hl7 terminology file is already present on the filesystem, it will use it.
     * Else, it will download the latest version or version @param version if specified.
     * If the LOINC terminology is supposed to be used as well, a conflicting codeSystem imported through hl7 must be removed ( see <a href="https://github.com/IHTSDO/snowstorm/issues/609"></a>)
     */
    @Override
    protected void importTerminology(SyndicationImportParams params) throws IOException, InterruptedException, ServiceException {
        Optional<File> file = findFile(workingDirectory, fileNamePattern);
        if (file.isEmpty()) {
            file = downloadFile(params.getVersion());
        }
        importHl7Package(file);
        if (params.isLoincImportIncluded()) {
            deleteConflictingHl7CodeSystem();
        }
    }

    private Optional<File> downloadFile(String version) throws IOException, InterruptedException {
        String versionSuffix = version == null ? "" : "@" + version;
        String packageName = "hl7.terminology." + fhirVersion + versionSuffix;
        Process process = new ProcessBuilder("npm", "--registry", "https://packages.simplifier.net", "pack", packageName)
                .directory(new File(workingDirectory))
                .start();
        waitForProcessTermination(process, "Download hl7 terminology");
        return findFile(workingDirectory, fileNamePattern);
    }

    private void importHl7Package(Optional<File> fileOpt) throws IOException, ServiceException {
        if (fileOpt.isEmpty()) {
            throw new ServiceException("Hl7 terminology file not found, cannot be imported");
        }
        File file = fileOpt.get();
        setActualTerminologyVersion(file.getName());
        logger.info("Importing HL7 Terminology from: {}", file.getName());
        loadPackageService.uploadPackageResources(file, Set.of("*"), file.getName(), false);
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
    protected void setActualTerminologyVersion(String releaseFileName) {
        String version = releaseFileName.replaceAll("^hl7\\.terminology\\.r4-(\\d+\\.\\d+\\.\\d+)\\.tgz$", "$1");
        actualVersion = isNotBlank(version) ? version : releaseFileName;
    }
}
