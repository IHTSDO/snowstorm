package org.snomed.snowstorm.fhir.rest;

import io.swagger.annotations.ApiOperation;

import java.util.List;

import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystem;
import org.snomed.snowstorm.fhir.services.FHIRMappingService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping(value = FHIRConstants.fhirDTSU3Root + "/CodeSystem", consumes = {"application/json", "application/xml"})
public class FHIRCodeSystemController implements FHIRConstants {

	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private FHIRMappingService mappingService;

	@ApiOperation("Retrieve all code systems")
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public org.snomed.snowstorm.rest.pojo.ItemsPage<FHIRCodeSystem> findAll() {
		List<CodeSystem> codeSystems = codeSystemService.findAll();
		return new ItemsPage<>(mappingService.mapToFHIR(codeSystems));
	}
	
	//@RequestMapping(value = "/$lookup", method = RequestMethod.GET)

}
