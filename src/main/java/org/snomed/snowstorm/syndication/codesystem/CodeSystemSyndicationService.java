package org.snomed.snowstorm.syndication.codesystem;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.JsonParser;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.CodeSystem;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRConceptService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.snomed.snowstorm.syndication.common.SyndicationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static org.snomed.snowstorm.core.util.FileUtils.findFiles;

public abstract class CodeSystemSyndicationService extends SyndicationService {

    @Autowired
    private FhirContext fhirContext;

    @Autowired
    private FHIRCodeSystemService codeSystemService;

    @Autowired
    private FHIRConceptService fhirConceptService;
    private String directory;
    private String codeSystemFilePattern;

    protected static final String DEFAULT_VERSION = "0";

    @PostConstruct
    public void init() {
        this.directory = "/app/" + getCodeSystemName();
        this.codeSystemFilePattern = getCodeSystemName() + "*-codesystem.*";
    }

    @Override
    protected List<File> fetchTerminologyPackages(SyndicationImportParams params) throws IOException, ServiceException {
        List<File> files = findFiles(directory, codeSystemFilePattern);
        if(files.isEmpty()) {
            throw new ServiceException("Codesystem file(s) not found with pattern " +
                    codeSystemFilePattern + " in directory " + directory + ", cannot be imported");
        }
        return files;
    }

    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws IOException, ServiceException {
        JsonParser jsonParser = (JsonParser) fhirContext.newJsonParser();
        for (File file : files) {
            CodeSystem codeSystem = (CodeSystem) jsonParser.parseResource(new FileInputStream(file));
            saveCodeSystemAndConcepts(codeSystem);
        }
    }

    protected void saveCodeSystemAndConcepts(CodeSystem codeSystem) throws ServiceException {
        FHIRCodeSystemVersion existing = codeSystemService.findCodeSystemVersion(new FHIRCodeSystemVersionParams(codeSystem.getUrl()).setVersion(codeSystem.getVersion()));
        if (existing != null) {
            logger.info("Deleting existing CodeSystem and concepts for url:{}, version:{}", existing.getUrl(), existing.getVersion());
            codeSystemService.deleteCodeSystemVersion(existing);
        }
        FHIRCodeSystemVersion codeSystemVersion = codeSystemService.createUpdate(codeSystem);
        fhirConceptService.saveAllConceptsOfCodeSystemVersion(codeSystem.getConcept(), codeSystemVersion);
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
