package org.snomed.snowstorm.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.rest.pojo.CreateCodeSystemVersionRequest;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "Admin", description = "Code Systems")
@RequestMapping(value = "/codesystems", produces = "application/json")
public class CodeSystemController {

	@Autowired
	private CodeSystemService codeSystemService;

	@ApiOperation("Create a code system")
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> createCodeSystem(@RequestBody CodeSystem codeSystem) {
		ControllerHelper.requiredParam(codeSystem.getShortName(), "shortName");
		ControllerHelper.requiredParam(codeSystem.getBranchPath(), "branchPath");
		codeSystemService.createCodeSystem(codeSystem);
		return ControllerHelper.getCreatedResponse(codeSystem.getShortName());
	}

	@ApiOperation("Retrieve all code systems")
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<CodeSystem> findAll() {
		return new ItemsPage<>(codeSystemService.findAll());
	}

	@ApiOperation("Retrieve a code system")
	@RequestMapping(value = "/{shortName}", method = RequestMethod.GET)
	@ResponseBody
	public CodeSystem findCodeSystem(@PathVariable String shortName) {
		return ControllerHelper.throwIfNotFound("Code System", codeSystemService.find(shortName));
	}

	@ApiOperation("Retrieve all code system versions")
	@RequestMapping(value = "/{shortName}/versions", method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<CodeSystemVersion> findAllVersions(@PathVariable String shortName) {
		return new ItemsPage<>(codeSystemService.findAllVersions(shortName));
	}

	@ApiOperation("Create a new code system version")
	@RequestMapping(value = "/{shortName}/versions", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> createVersion(@PathVariable String shortName, @RequestBody CreateCodeSystemVersionRequest input) {
		CodeSystem codeSystem = codeSystemService.find(shortName);
		ControllerHelper.throwIfNotFound("CodeSystem", codeSystem);

		String versionId = codeSystemService.createVersion(codeSystem, input.getEffectiveDate(), input.getDescription());
		return ControllerHelper.getCreatedResponse(versionId);
	}

}
