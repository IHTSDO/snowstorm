package org.snomed.snowstorm.syndication.services.importers.customversion.icd10be;

import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;

import org.hl7.fhir.r4.model.CodeSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Icd10BeSheetContentHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
    private final boolean[] isClinicalModification;
    private final Map<String, CodeSystem.ConceptDefinitionComponent> cmConcepts;
    private final Map<String, CodeSystem.ConceptDefinitionComponent> procedureCodeConcepts;
    int codeIdx, frIdx, nlIdx, enIdx;
    final List<String> rowValues;

    private static final String FIRST_PROCEDURECODE = "001";

    public Icd10BeSheetContentHandler(boolean[] isClinicalModification, Map<String, CodeSystem.ConceptDefinitionComponent> cmConcepts, Map<String, CodeSystem.ConceptDefinitionComponent> procedureCodeConcepts) {
        this.isClinicalModification = isClinicalModification;
        this.cmConcepts = cmConcepts;
        this.procedureCodeConcepts = procedureCodeConcepts;
        codeIdx = -1;
        frIdx = -1;
        nlIdx = -1;
        enIdx = -1;
        rowValues = new ArrayList<>();
    }

    @Override
    public void startRow(int rowNum) {
        rowValues.clear();
    }

    @Override
    public void endRow(int rowNum) {
        if (rowNum == 0) { // header row
            for (int i = 0; i < rowValues.size(); i++) {
                switch (rowValues.get(i)) {
                    case "ICDCODE" -> codeIdx = i;
                    case "ICDTXTFR" -> frIdx = i;
                    case "ICDTXTNL" -> nlIdx = i;
                    case "ICDTXTEN" -> enIdx = i;
                }
            }
            if (codeIdx == -1 || frIdx == -1 || nlIdx == -1 || enIdx == -1) {
                throw new IllegalStateException("One or more required columns not found.");
            }
        } else {
            if (rowValues.size() <= Math.max(codeIdx, Math.max(frIdx, Math.max(nlIdx, enIdx)))) {
                return;
            }

            String code = rowValues.get(codeIdx);
            String fr = rowValues.get(frIdx);
            String nl = rowValues.get(nlIdx);
            String en = rowValues.get(enIdx);

            if (code == null || code.isBlank()) return;

            if(FIRST_PROCEDURECODE.equals(code)) {
                isClinicalModification[0] = false;
            }

            if(isClinicalModification[0]) {
                if(code.length() > 3 && code.charAt(3) != '.') {
                    code = code.substring(0, 3) + "." + code.substring(3);
                }
                cmConcepts.put(code, makeConcept(code, en, fr, nl));
            } else {
                procedureCodeConcepts.put(code, makeConcept(code, en, fr, nl));
            }
        }
    }

    @Override
    public void cell(String cellReference, String formattedValue, XSSFComment comment) {
        rowValues.add(formattedValue != null ? formattedValue : "");
    }

    @Override
    public void headerFooter(String text, boolean isHeader, String tagName) {}

    private CodeSystem.ConceptDefinitionComponent makeConcept(String code, String en, String fr, String nl) {
        CodeSystem.ConceptDefinitionComponent concept = new CodeSystem.ConceptDefinitionComponent();
        concept.setCode(code);
        concept.setDisplay(en);

        List<CodeSystem.ConceptDefinitionDesignationComponent> designations = new ArrayList<>();

        if (fr != null && !fr.isBlank()) {
            designations.add(makeDesignation("fr", fr));
        }
        if (nl != null && !nl.isBlank()) {
            designations.add(makeDesignation("nl", nl));
        }
        if (en != null && !en.isBlank()) {
            designations.add(makeDesignation("en", en));
        }

        concept.setDesignation(designations);
        return concept;
    }

    private CodeSystem.ConceptDefinitionDesignationComponent makeDesignation(String lang, String value) {
        return new CodeSystem.ConceptDefinitionDesignationComponent()
                .setLanguage(lang)
                .setValue(value);
    }
}
