package org.snomed.snowstorm.fhir.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.ApiOperation;

import java.util.List;

import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.resource.FHIRResource;
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.snomed.snowstorm.fhir.services.FHIRMappingService;
import org.snomed.snowstorm.fhir.services.FHIROperationException;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = FHIRConstants.FHIR_DTSU3_ROOT + "/CodeSystem/$lookup")
public class FHIRCodeSystemLookupController implements FHIRConstants {

	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private FHIRMappingService mappingService;
	
	private FHIRHelper helper = new FHIRHelper();

	@ApiOperation("Lookup specific codes in specified system")
	@RequestMapping(method = {RequestMethod.GET, RequestMethod.POST} )
	public FHIRResource lookup(
			@RequestParam(required = false) String code,
			@RequestParam(required = true, defaultValue = SNOMED_URI) String system,
			@RequestParam(required = false) String version,
			@RequestParam(required = false) String date,
			@RequestParam(required = false, defaultValue = LANG_EN) String lang,
			@RequestParam(value = "property", required = false) List<String> properties	
			) throws FHIROperationException {
		
		if (system == null || system.isEmpty() || !system.equals(SNOMED_URI)) {
			return helper.validationFailure("System must be present, and currently only " + SNOMED_URI + " is supported.");
		}
		String branch = helper.getBranchForVersion(version);
		Concept c = ControllerHelper.throwIfNotFound("Concept", conceptService.find(code, BranchPathUriUtil.parseBranchPath(branch)));
		return mappingService.mapToFHIR(c); 
	}

}
