package org.snomed.snowstorm.syndication.services.importers.fixedversion.atc;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeSystem;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.services.importers.fixedversion.FixedVersionSyndicationService;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.ATC_CODESYSTEM;

@Service(ATC_CODESYSTEM)
public class AtcSyndicationService extends FixedVersionSyndicationService {

    private static final int L1_CODE_COLUMN = 0;
    private static final int L1_NAME_COLUMN = 2;
    private static final int L2_CODE_COLUMN = 3;
    private static final int L2_NAME_COLUMN = 5;
    private static final int L3_CODE_COLUMN = 6;
    private static final int L3_NAME_COLUMN = 8;
    private static final int L4_CODE_COLUMN = 9;
    private static final int L4_NAME_COLUMN = 11;
    private static final int L5_CODE_COLUMN = 12;
    private static final int L5_NAME_COLUMN = 13;
    private static final int[][] CODE_NAME_COLUMNS = {
            {L1_CODE_COLUMN, L1_NAME_COLUMN},
            {L2_CODE_COLUMN, L2_NAME_COLUMN},
            {L3_CODE_COLUMN, L3_NAME_COLUMN},
            {L4_CODE_COLUMN, L4_NAME_COLUMN},
            {L5_CODE_COLUMN, L5_NAME_COLUMN}
    };


    @Override
    protected String getCodeSystemName() {
        return ATC_CODESYSTEM;
    }

    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws ServiceException {
        CodeSystem codeSystem = new CodeSystem();
        codeSystem.setUrl("http://whocc.no/atc");
        codeSystem.setName("ATC/DDD index");
        codeSystem.setVersion(DEFAULT_VERSION);
        codeSystem.setConcept(readConceptsFromFile(files));
        saveCodeSystemAndConcepts(codeSystem);
    }

    private static List<CodeSystem.ConceptDefinitionComponent> readConceptsFromFile(List<File> codeSystemFiles) throws ServiceException {
        Map<String, String> codes = new HashMap<>();

        File file = codeSystemFiles.get(0);
        try (Reader reader = new FileReader(file)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader().withSkipHeaderRecord().parse(reader);

            for (CSVRecord record : records) {
                for(int[] codeName: CODE_NAME_COLUMNS) {
                    String code = record.get(codeName[0]).trim();
                    String display = record.get(codeName[1]).trim();

                    if (!code.isEmpty()) {
                        codes.put(code, display);
                    }
                }
            }
        } catch (Exception e) {
            throw new ServiceException("Failed to read CSV file: " + file.getName(), e);
        }
        return toConcepts(codes);
    }

    private static List<CodeSystem.ConceptDefinitionComponent> toConcepts(Map<String, String> codes) {
        List<CodeSystem.ConceptDefinitionComponent> concepts = new ArrayList<>();
        codes.forEach((code, display) -> {
            CodeSystem.ConceptDefinitionComponent concept = new CodeSystem.ConceptDefinitionComponent();
            concept.setCode(code);
            concept.setDisplay(display);
            concepts.add(concept);
        });
        return concepts;
    }
}
