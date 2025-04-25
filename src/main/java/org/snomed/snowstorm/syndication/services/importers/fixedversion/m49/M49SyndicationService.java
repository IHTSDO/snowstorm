package org.snomed.snowstorm.syndication.services.importers.fixedversion.m49;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.services.importers.fixedversion.FixedVersionSyndicationService;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.M49_CODESYSTEM;

@Service(M49_CODESYSTEM)
public class M49SyndicationService extends FixedVersionSyndicationService {

    @Override
    protected String getCodeSystemName() {
        return M49_CODESYSTEM;
    }

    @Override
    protected List<File> fetchTerminologyPackages(SyndicationImportParams params) {
        return List.of();
    }

    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws ServiceException {
        CodeSystem codeSystem = new CodeSystem();
        codeSystem.setUrl("http://unstats.un.org/unsd/methods/m49/m49.htm");
        codeSystem.setConcept(List.of(createConcept()));
        codeSystem.setName("Standard country or area codes for statistical use (M49)");
        codeSystem.setVersion(DEFAULT_VERSION);
        saveCodeSystemAndConcepts(codeSystem);
    }

    private static CodeSystem.ConceptDefinitionComponent createConcept() {
        CodeSystem.ConceptDefinitionComponent concept = new CodeSystem.ConceptDefinitionComponent();
        concept.setCode("001");
        concept.setDisplay("World");
        CodeSystem.ConceptPropertyComponent conceptProperty = new CodeSystem.ConceptPropertyComponent();
        conceptProperty.setCode("class");
        conceptProperty.setValue(new StringType("region"));
        concept.setProperty("property", conceptProperty);
        return concept;
    }
}
