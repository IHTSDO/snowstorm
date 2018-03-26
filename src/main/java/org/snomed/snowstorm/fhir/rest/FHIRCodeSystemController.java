package org.snomed.snowstorm.fhir.rest;

import io.swagger.annotations.ApiOperation;

import java.util.List;

import org.hl7.fhir.dstu3.model.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRMappingService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping(value = "/codesystems")
//Note that we do not specify consumes or produces here, the framework will figure it out.
public class FHIRCodeSystemController {

	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private FHIRMappingService mappingService;

	@ApiOperation("Retrieve all code systems")
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public org.snomed.snowstorm.rest.pojo.ItemsPage<CodeSystem> findAll() {
		List<org.snomed.snowstorm.core.data.domain.CodeSystem> codeSystems = codeSystemService.findAll();
		return new ItemsPage<>(mappingService.mapToFHIR(codeSystems));
	}

/*	@ApiOperation("Retrieve a code system")
	@RequestMapping(value = "/{shortName}", method = RequestMethod.GET)
	@ResponseBody
	public CodeSystem findClassification(@PathVariable String shortName) {
		return ControllerHelper.throwIfNotFound("Code System", codeSystemService.find(shortName));
	}

	@ApiOperation("Retrieve all code system versions")
	@RequestMapping(value = "/{shortName}/versions", method = RequestMethod.GET)
	@ResponseBody
	public org.snomed.snowstorm.rest.pojo.ItemsPage<CodeSystemVersion> findAllVersions(@PathVariable String shortName) {
		return new ItemsPage<>(codeSystemService.findAllVersions(shortName));
	}*/

}
