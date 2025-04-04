package org.snomed.snowstorm.syndication.snomed;

import org.apache.logging.log4j.util.Strings;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.syndication.SyndicationService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import static org.snomed.snowstorm.core.data.services.CodeSystemService.MAIN;
import static org.snomed.snowstorm.core.util.FileUtils.removeTempFiles;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.SNOMED_URI_MODULE_AND_VERSION_PATTERN;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.IMPORT_SNOMED_TERMINOLOGY;

@Service(IMPORT_SNOMED_TERMINOLOGY)
public class SnomedSyndicationService extends SyndicationService {

    @Value("${SNOMED_USERNAME}")
    private String snomedUsername;

    @Value("${SNOMED_PASSWORD}")
    private String snomedPassword;

    @Autowired
    private SnomedSyndicationClient syndicationClient;

    @Autowired
    private ImportService importService;

    @Autowired
    private CodeSystemService codeSystemService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SnomedSyndicationService() {
        super("Snomed");
    }

    @Override
    protected void importTerminology(SyndicationImportParams params) throws IOException, ServiceException {
        String releaseUri = params.getVersion();
        validateReleaseUriPattern(releaseUri);
        validateSyndicationCredentials();
        List<String> filePaths = syndicationClient.downloadPackages(releaseUri, snomedUsername, snomedPassword);
        setActualTerminologyVersion(releaseUri);
        importEdition(filePaths);
        importExtension(releaseUri, params.getExtensionName(), filePaths);
        removeTempFiles(filePaths);
    }

    private static void validateReleaseUriPattern(String releaseUri) {
        if (!SNOMED_URI_MODULE_AND_VERSION_PATTERN.matcher(releaseUri).matches()) {
            throw new IllegalArgumentException("Parameter ' " + IMPORT_SNOMED_TERMINOLOGY + " ' is not a valid SNOMED CT Edition Version URI. " +
                    "Please use the format: 'http://snomed.info/sct/[module-id]/version/[YYYYMMDD]'. " +
                    "See http://snomed.org/uri for examples of Edition version URIs");
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

    private void importEdition(List<String> filePaths) {
        importPackage(filePaths.get(0), MAIN);
        logger.info("Edition import DONE");
    }

    private void importExtension(String releaseUri, String extensionCountryCode, List<String> filePaths) throws ServiceException {
        if (filePaths.size() > 1 && extensionCountryCode == null) {
            throw new IllegalArgumentException("ReleaseURI: '" + releaseUri + "' is an extension. An extension name must be specified.");
        }
        if (filePaths.size() > 1) {
            String shortName = "SNOMED-" + extensionCountryCode;
            String branchPath = MAIN + "/" + shortName;
            codeSystemService.createCodeSystem(new CodeSystem(shortName, branchPath, extensionCountryCode + " edition", extensionCountryCode.toLowerCase()));
            importPackage(filePaths.get(1), branchPath);
            logger.info("Extension import DONE");
        }
    }

    private void importPackage(String filePath, String branchName) {
        String importId = importService.createJob(RF2Type.SNAPSHOT, branchName, true, false);
        try (FileInputStream releaseFileStream = getFileInputStream(filePath)) {
            importService.importArchive(importId, releaseFileStream);
        } catch (IOException | ReleaseImportException e) {
            logger.error("Import failed.", e);
        }
    }

    protected FileInputStream getFileInputStream(String filePath) throws FileNotFoundException {
        return new FileInputStream(filePath);
    }

    @Override
    protected void setActualTerminologyVersion(String releaseFileName) {
        actualVersion = releaseFileName; // todo: adapt during snomed refactoring
    }
}
