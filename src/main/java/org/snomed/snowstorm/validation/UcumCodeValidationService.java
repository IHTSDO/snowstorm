package org.snomed.snowstorm.validation;

import jakarta.annotation.PostConstruct;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;
import org.fhir.ucum.UcumService;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.util.FileUtils;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class UcumCodeValidationService {

    @Value("${syndication.ucum.working-directory}")
    private String workingDirectory;

    private UcumService ucumService;

    public static final String FILENAME = "ucum-essence.xml";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void init() throws IOException, UcumException {
        try {
            File essenceFile = FileUtils.findFile(workingDirectory, FILENAME).orElseThrow();
            ucumService = new UcumEssenceService(essenceFile.getPath());
        } catch (Exception e) {
            logger.warn("UcumCodeValidationService initialization error: Could not find UCUM essence file: " + FILENAME + " in {}", workingDirectory);
        }
    }

    public Parameters validateCode(String code, FHIRCodeSystemVersionParams codeSystemParams) {
        String error;
        if(ucumService == null) {
            error = "Validation service was not correctly initialized, impossible to perform validation";
        } else {
            error = ucumService.validate(code);
        }
        Parameters response = new Parameters();
        response.addParameter("result", new BooleanType(error == null));
        response.addParameter("system", new UriType(codeSystemParams.getCodeSystem()));
        response.addParameter("code", new CodeType(code));
        response.addParameter("version", new StringType(ucumService.ucumIdentification().getVersion()));
        response.addParameter("display", new StringType(code));
        if(error != null) {
            response.addParameter("message", new StringType(error));
        }
        return response;
    }
}
