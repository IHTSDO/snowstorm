package org.snomed.snowstorm.syndication.hl7;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRLoadPackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static org.snomed.snowstorm.core.util.FileUtils.findFile;
import static org.snomed.snowstorm.syndication.SyndicationUtils.waitForProcessTermination;

@Service
public class Hl7SyndicationService {

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

    /**
     * Will import the hl7 terminology. If a hl7 terminology file is already present on the filesystem, it will use it.
     * Else, it will download the latest version or version @param version if specified
     *
     * @param version     The version to download. E.g. null (latest version) or "6.2.0"
     * @param importLoinc Whether the LOINC terminology is supposed to be used as well.
     *                    In that case, a conflicting codeSystem imported through hl7 must be removed ( see <a href="https://github.com/IHTSDO/snowstorm/issues/609"></a>)
     */
    public void importHl7Terminology(String version, boolean importLoinc) throws IOException, InterruptedException, ServiceException {
        Optional<File> file = findFile(workingDirectory, fileNamePattern);
        if(file.isEmpty()) {
            file = downloadFile(version);
        }
        importHl7Package(file);
        if(importLoinc) {
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
        logger.info("Importing HL7 Terminology from: {}", file.getName());
        loadPackageService.uploadPackageResources(file, Set.of("*"), file.getName(), false);
    }

    private void deleteConflictingHl7CodeSystem() {
        FHIRCodeSystemVersionParams codeSystemVersionParams = new FHIRCodeSystemVersionParams("http://loinc.org");
        codeSystemVersionParams.setId("v3-loinc");
        FHIRCodeSystemVersion codeSystemVersion = fhirCodeSystemService.findCodeSystemVersion(codeSystemVersionParams);
        if(codeSystemVersion != null) {
            fhirCodeSystemService.deleteCodeSystemVersion(codeSystemVersion);
        }
    }
}
