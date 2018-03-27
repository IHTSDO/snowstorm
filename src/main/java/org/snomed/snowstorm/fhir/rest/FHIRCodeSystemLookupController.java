package org.snomed.snowstorm.fhir.rest;

import io.swagger.annotations.ApiOperation;

import java.util.List;

import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.resource.FHIRBundle;
import org.snomed.snowstorm.fhir.domain.resource.FHIRResource;
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.snomed.snowstorm.fhir.services.FHIRMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = FHIRConstants.FHIR_DTSU3_ROOT + "/CodeSystem/$lookup", consumes = {"application/json", "application/xml"})
public class FHIRCodeSystemLookupController implements FHIRConstants {

	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private FHIRMappingService mappingService;
	private FHIRHelper helper;

	@ApiOperation("Lookup specific codes in specified system")
	@RequestMapping(method = {RequestMethod.GET, RequestMethod.POST} )
	public FHIRResource lookup(
			@RequestParam(required = false) String code,
			@RequestParam(required = true, defaultValue = SNOMED_URI) String system,
			@RequestParam(required = false) String version,
			@RequestParam(required = false) String date,
			@RequestParam(required = false, defaultValue = LANG_EN) String lang,
			@RequestParam(value = "property", required = false) List<String> properties	
			) {
		
		if (system == null || system.isEmpty() || !system.equals(SNOMED_URI)) {
			return helper.validationFailure("System must be present, and currently only " + SNOMED_URI + " is supported.");
		}
		List<CodeSystem> codeSystems = codeSystemService.findAll();
		return new FHIRBundle(mappingService.mapToFHIR(codeSystems));
	}

}
