package org.snomed.snowstorm.syndication.services.importers.customversion.snomed;

import org.apache.logging.log4j.util.Strings;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.syndication.models.integration.SyndicationFeedEntry;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.data.services.CodeSystemService.MAIN;
import static org.snomed.snowstorm.syndication.utils.FileUtils.findFile;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.SNOMED_URI_MODULE_AND_VERSION_PATTERN;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.SNOMED_URI_MODULE_PATTERN;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOCAL_VERSION;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.SNOMED_TERMINOLOGY;

@Service(SNOMED_TERMINOLOGY)
public class SnomedSyndicationService extends SyndicationService {

    @Value("${SNOMED_USERNAME:empty}")
    private String snomedUsername;

    @Value("${SNOMED_PASSWORD:empty}")
    private String snomedPassword;

    @Value("${syndication.snomed.working-directory}")
    private String workingDirectory;

    @Value("${syndication.snomed.fileNamePattern.edition}")
    private String editionFileNamePattern;

    @Value("${syndication.snomed.fileNamePattern.extension}")
    private String extensionFileNamePattern;

    @Autowired
    private SnomedSyndicationClient syndicationClient;

    @Autowired
    private ImportService importService;

    @Autowired
    private CodeSystemService codeSystemService;

    private static final String DEFAULT_VALUE = "empty";

    private String releaseUri;

    @Override
    protected List<File> fetchTerminologyPackages(SyndicationImportParams params) throws ServiceException, IOException {
        releaseUri = params.version();
        if(LOCAL_VERSION.equals(releaseUri)) {
            return retrieveLocalPackages(params);
        }
        releaseUri = releaseUri.contains("version") ? releaseUri : getLatestTerminologyVersion(params.version());
        validateReleaseUriAndVersionPattern(releaseUri);
        validateSyndicationCredentials();
        return syndicationClient.downloadPackages(releaseUri, snomedUsername, snomedPassword);
    }

    /**
     * Will import the snomed terminology. If the version provided in the params is an extension, it will import the linked edition as well
     */
    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws ServiceException {
        String releaseUri = params.version();
        List<String> packageFilePaths = files.stream().map(File::getAbsolutePath).collect(Collectors.toList());
        importEdition(packageFilePaths);
        importExtension(releaseUri, params.extensionName(), packageFilePaths);
    }

    private List<File> retrieveLocalPackages(SyndicationImportParams params) throws ServiceException, IOException {
        List<File> packageFilePaths = new ArrayList<>();
        packageFilePaths.add(
                findFile(workingDirectory, editionFileNamePattern)
                        .orElseThrow(() -> new ServiceException("Could not find edition file with pattern " + editionFileNamePattern)));
        if(params.extensionName() != null) {
            packageFilePaths.add(
                    findFile(workingDirectory, extensionFileNamePattern)
                            .orElseThrow(() -> new ServiceException("Could not find extension file with pattern " + extensionFileNamePattern)));
        }
        return packageFilePaths;
    }

    private static void validateReleaseUriAndVersionPattern(String releaseUri) {
        if (!SNOMED_URI_MODULE_AND_VERSION_PATTERN.matcher(releaseUri).matches()) {
            throw new IllegalArgumentException("Parameter.version is not a valid SNOMED CT release version URI. " +
                    "Please use the format: 'http://snomed.info/sct/[module-id]/version/[YYYYMMDD]'. " +
                    "See https://confluence.ihtsdotools.org/display/DOCURI/2.1+URIs+for+Editions+and+Versions for examples of release version URIs");
        }
    }

    private static void validateReleaseUriPattern(String releaseUri) {
        if (!SNOMED_URI_MODULE_PATTERN.matcher(releaseUri).matches()) {
            throw new IllegalArgumentException("Parameter.version is not a valid SNOMED CT release URI. " +
                    "Please use the format: 'http://snomed.info/sct/[module-id]'. " +
                    "See https://confluence.ihtsdotools.org/display/DOCEXTPG/4.4.2+Edition+URI+Examples for examples of release URIs");
        }
    }

    private void validateSyndicationCredentials() {
        if (Strings.isBlank(snomedUsername) || DEFAULT_VALUE.equals(snomedUsername)) {
            logger.error("Syndication username is blank.");
            throw new IllegalArgumentException("Syndication username is blank.");
        }
        if (Strings.isBlank(snomedPassword) || DEFAULT_VALUE.equals(snomedPassword)) {
            logger.error("Syndication password is blank.");
            throw new IllegalArgumentException("Syndication password is blank.");
        }
    }

    private void importEdition(List<String> filePaths) throws ServiceException {
        importPackage(filePaths.get(0), MAIN);
        logger.info("Edition import DONE");
    }

    private void importExtension(String releaseUri, String extensionCountryCode, List<String> filePaths) throws ServiceException {
        if (filePaths.size() > 1 && extensionCountryCode == null) {
            throw new IllegalArgumentException("ReleaseURI: '" + releaseUri + "' is an extension. An extension name must be specified.");
        }
        if (filePaths.size() > 1) {
            String shortName = "SNOMEDCT-" + extensionCountryCode;
            String branchPath = MAIN + "/" + shortName;
            CodeSystem newCodeSystem = new CodeSystem(shortName, branchPath, extensionCountryCode + " edition", extensionCountryCode.toLowerCase());
            newCodeSystem.setUriModuleId(getModuleUri(releaseUri));
            codeSystemService.createCodeSystem(newCodeSystem);
            importPackage(filePaths.get(1), branchPath);
            logger.info("Extension import DONE");
        }
    }

    private void importPackage(String filePath, String branchName) throws ServiceException {
        String importId = importService.createJob(RF2Type.SNAPSHOT, branchName, true, false);
        try (FileInputStream releaseFileStream = getFileInputStream(filePath)) {
            importService.importArchive(importId, releaseFileStream);
        } catch (IOException | ReleaseImportException e) {
            logger.error("Import failed.", e);
            throw new ServiceException("Import package failed .", e);
        }
    }

    protected FileInputStream getFileInputStream(String filePath) throws FileNotFoundException {
        return new FileInputStream(filePath);
    }

    @Override
    protected String getTerminologyVersion(String releaseFileName) {
        return releaseUri;
    }

    @Override
    protected String getLatestTerminologyVersion(String releaseUri) throws IOException, ServiceException {
        validateReleaseUriPattern(releaseUri);
        return syndicationClient.getFeed().getEntries().stream()
                .map(SyndicationFeedEntry::getContentItemVersion)
                .filter(contentItemVersion -> contentItemVersion.contains(releaseUri))
                .findFirst()
                .orElseThrow(() -> new ServiceException("No snomed release found related to the supplied release URI: " + releaseUri));
    }

    /**
     *
     * @param releaseUri e.g. http ://snomed.info/sct/11000172109/, local, http ://snomed.info/sct/11000172109/version/20250315, ...
     * @return the imported moduleUri or null if not found, e.g. 11000172109
     */
    private String getModuleUri(String releaseUri) {
        try {
            Matcher matcher = Pattern.compile("sct/(\\d+)").matcher(releaseUri);
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
