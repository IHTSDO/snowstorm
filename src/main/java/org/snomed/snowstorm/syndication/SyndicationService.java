package org.snomed.snowstorm.syndication;

import org.apache.logging.log4j.util.Strings;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static org.snomed.snowstorm.SnowstormApplication.SNOMED_VERSION;
import static org.snomed.snowstorm.core.data.services.CodeSystemService.MAIN;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.SNOMED_URI_MODULE_AND_VERSION_PATTERN;

@Service
public class SyndicationService {

    @Value("${SNOMED_USERNAME}")
    private String snomedUsername;

    @Value("${SNOMED_PASSWORD}")
    private String snomedPassword;

    @Autowired
    private SyndicationClient syndicationClient;

    @Autowired
    private ImportService importService;

    @Autowired
    private CodeSystemService codeSystemService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void importSnomed(String releaseUri, String extensionCountryCode) throws IOException, ServiceException {
        if (Strings.isBlank(releaseUri)) {
            throw new IllegalArgumentException("Parameter ' " + SNOMED_VERSION + " ' must be set when loading SNOMED via syndication.");
        }
        if (!SNOMED_URI_MODULE_AND_VERSION_PATTERN.matcher(releaseUri).matches()) {
            throw new IllegalArgumentException("Parameter ' " + SNOMED_VERSION + " ' is not a valid SNOMED CT Edition Version URI. " +
                    "Please use the format: 'http://snomed.info/sct/[module-id]/version/[YYYYMMDD]'. " +
                    "See http://snomed.org/uri for examples of Edition version URIs");
        }
        validateSyndicationCredentials();
        List<String> filePaths = syndicationClient.downloadPackages(releaseUri, snomedUsername, snomedPassword);
        importPackage(filePaths.get(0), MAIN);
        logger.info("Edition import DONE");
        if(filePaths.size() > 1 && extensionCountryCode == null) {
            throw new IllegalArgumentException("ReleaseURI: '" + releaseUri + "' is an extension. An extension name must be specified.");
        }
        if(filePaths.size() > 1) {
            String shortName = "SNOMED-" + extensionCountryCode;
            String branchPath = MAIN + "/" + shortName;
            codeSystemService.createCodeSystem(new CodeSystem(shortName, branchPath, extensionCountryCode + " edition", extensionCountryCode.toLowerCase()));
            importPackage(filePaths.get(1), branchPath);
            logger.info("Extension import DONE");
        }
        for (String filePath : filePaths) {
            if (!new File(filePath).delete()) {
                logger.warn("Failed to delete temp file {}", filePath);
            }
        }
    }

    private void importPackage(String filePath, String branchName) {
        String importId = importService.createJob(RF2Type.SNAPSHOT, branchName, true, false);
        try (FileInputStream releaseFileStream = new FileInputStream(filePath)) {
            importService.importArchive(importId, releaseFileStream);
        } catch (IOException | ReleaseImportException e) {
            logger.error("Import failed.", e);
        }
    }

    private void validateSyndicationCredentials() {
        if (Strings.isBlank(snomedUsername)) {
            logger.error("Syndication username is blank.");
            throw new IllegalArgumentException("Syndication username is blank.");
        }
        if (Strings.isBlank(snomedPassword)) {
            logger.error("Syndication password is blank.");
            throw new IllegalArgumentException("Syndication password is blank.");
        }
    }
}
