package org.snomed.snowstorm.syndication.services.importers.fixedversion.ucum;

import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumService;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.services.importers.fixedversion.FixedVersionSyndicationService;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static java.lang.String.format;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.UCUM_URI;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.UCUM_CODESYSTEM;

@Service(UCUM_CODESYSTEM)
public class UcumCodeValidationService extends FixedVersionSyndicationService {

    private UcumService ucumService;

    @Override
    protected String getCodeSystemName() {
        return UCUM_CODESYSTEM;
    }

    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws ServiceException {
        try {
            ucumService = new UcumEssenceService(files.get(0).getPath());
        } catch (Exception e) {
            throw new ServiceException("Unable to initialize the UCUM essence service", e);
        }
    }

    public Parameters validateCode(String code, String codeSystem) {
        String error;
        StringType version;
        if(ucumService == null) {
            error = "The UCUM essence service is not initialized";
            version = new StringType();
        } else {
            error = ucumService.validate(code);
            version = new StringType(ucumService.ucumIdentification().getVersion());
        }
        Parameters response = new Parameters();
        response.addParameter("result", new BooleanType(error == null));
        response.addParameter("system", new UriType(codeSystem));
        response.addParameter("code", new CodeType(code));
        response.addParameter("version", version);
        response.addParameter("display", new StringType(code));
        if(error != null) {
            response.addParameter("message", new StringType(error));
        }
        return response;
    }

    public Parameters validateCodeWithValueSet(String code, ValueSet valueSet) {
        Parameters response = validateCode(code, UCUM_URI);
        if(!response.hasParameter("message") && !valueSetIncludesCode(valueSet, code)) {
            response.addParameter("message", new StringType(format("The provided code '%s#%s' was not found in the value " +
                    "set '%s|%s'", UCUM_URI, code, valueSet.getUrl(), valueSet.getVersion())));
        }
        return response;
    }

    private boolean valueSetIncludesCode(ValueSet valueSet, String code) {
        if(valueSet.getCompose().hasInclude()) {
            return valueSet.getCompose().getInclude().stream()
                    .filter(i -> i.getSystem().equals(UCUM_URI))
                    .findFirst()
                    .map(ValueSet.ConceptSetComponent::getConcept)
                    .stream()
                    .flatMap(Collection::stream)
                    .anyMatch(concept -> code.equals(concept.getCode()));
        }
        return false;
    }
}
