package org.snomed.snowstorm.syndication.codesystem.bcp;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeSystem;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.codesystem.CodeSystemSyndicationService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static org.snomed.snowstorm.syndication.common.SyndicationConstants.BCP13_CODESYSTEM;

@Service(BCP13_CODESYSTEM)
public class Bcp13SyndicationService extends CodeSystemSyndicationService {

    @Override
    protected String getCodeSystemName() {
        return BCP13_CODESYSTEM;
    }

    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws ServiceException {
        CodeSystem codeSystem = new CodeSystem();
        codeSystem.setUrl("urn:ietf:bcp:13");
        codeSystem.setName("IETF Media Types");
        codeSystem.setVersion(DEFAULT_VERSION);
        codeSystem.setConcept(readConceptsFromFile(files));
        saveCodeSystemAndConcepts(codeSystem);
    }

    private static List<CodeSystem.ConceptDefinitionComponent> readConceptsFromFile(List<File> codeSystemFiles) throws ServiceException {
        List<CodeSystem.ConceptDefinitionComponent> concepts = new ArrayList<>();
        for(File file : codeSystemFiles) {
            try (Reader reader = new FileReader(file)) {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader().withSkipHeaderRecord().parse(reader);

                for (CSVRecord record : records) {
                    String display = record.get(0).trim();
                    String code = record.get(1).trim();

                    if (!code.isEmpty()) {
                        CodeSystem.ConceptDefinitionComponent concept = new CodeSystem.ConceptDefinitionComponent();
                        concept.setCode(code);
                        concept.setDisplay(display);
                        concepts.add(concept);
                    }
                }
            } catch (Exception e) {
                throw new ServiceException("Failed to read CSV file: " + file.getName(), e);
            }
        }
        return concepts;
    }
}
