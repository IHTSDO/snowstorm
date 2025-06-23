package org.snomed.snowstorm.syndication.services.importers.fixedversion;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.JsonParser;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.CodeSystem;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static org.snomed.snowstorm.syndication.utils.FileUtils.findFiles;

public abstract class FixedVersionSyndicationService extends SyndicationService {

    @Autowired
    private FhirContext fhirContext;

    @Value("${syndication.root-directory:/app}")
    private String syndicationRootDirectory;

    protected String directory;
    protected String codeSystemFilePattern;

    protected static final String DEFAULT_VERSION = "0";

    @PostConstruct
    public void init() {
        this.directory = syndicationRootDirectory + "/" + getCodeSystemName();
        this.codeSystemFilePattern = getCodeSystemName() + "*-codesystem.*";
    }

    @Override
    protected List<File> fetchTerminologyPackages(SyndicationImportParams params) throws IOException, ServiceException {
        return findFiles(directory, codeSystemFilePattern);
    }

    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws IOException, ServiceException {
        JsonParser jsonParser = (JsonParser) fhirContext.newJsonParser();
        for (File file : files) {
            CodeSystem codeSystem = (CodeSystem) jsonParser.parseResource(new FileInputStream(file));
            saveCodeSystemAndConcepts(codeSystem);
        }
    }

    @Override
    protected String getLatestTerminologyVersion(String params) {
        return DEFAULT_VERSION;
    }

    @Override
    protected String getTerminologyVersion(String releaseFileName) {
        return DEFAULT_VERSION;
    }

    protected abstract String getCodeSystemName();
}
