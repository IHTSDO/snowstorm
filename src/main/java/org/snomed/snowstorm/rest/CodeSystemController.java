package org.snomed.snowstorm.rest;

import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/codesystems", produces = "application/json")
public class CodeSystemController {

	@Autowired
	private CodeSystemService codeSystemService;

	@ApiOperation("Retrieve all code systems")
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<CodeSystem> findAll() {
		return new ItemsPage<>(codeSystemService.findAll());
	}

	@ApiOperation("Retrieve a code system")
	@RequestMapping(value = "/{shortName}", method = RequestMethod.GET)
	@ResponseBody
	public CodeSystem findClassification(@PathVariable String shortName) {
		return ControllerHelper.throwIfNotFound("Code System", codeSystemService.find(shortName));
	}

	@ApiOperation("Retrieve all code system versions")
	@RequestMapping(value = "/{shortName}/versions", method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<CodeSystemVersion> findAllVersions(@PathVariable String shortName) {
		return new ItemsPage<>(codeSystemService.findAllVersions(shortName));
	}

}
