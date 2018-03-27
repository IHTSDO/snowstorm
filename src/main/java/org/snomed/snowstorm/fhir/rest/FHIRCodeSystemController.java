package org.snomed.snowstorm.fhir.rest;

import io.swagger.annotations.ApiOperation;

import java.util.List;

import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.resource.FHIRBundle;
import org.snomed.snowstorm.fhir.domain.resource.FHIRResource;
import org.snomed.snowstorm.fhir.services.FHIRMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = FHIRConstants.FHIR_DTSU3_ROOT + "/CodeSystem", consumes = {"application/json", "application/xml"})
public class FHIRCodeSystemController implements FHIRConstants {

	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private FHIRMappingService mappingService;

	@ApiOperation("Retrieve all code systems")
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public FHIRResource findAll() {
		List<CodeSystem> codeSystems = codeSystemService.findAll();
		return new FHIRBundle(mappingService.mapToFHIR(codeSystems));
	}
}
