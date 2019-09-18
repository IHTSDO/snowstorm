package org.snomed.snowstorm.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.fieldpermissions.CodeSystemCreate;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.rest.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "Code Systems", description = "-")
@RequestMapping(value = "/codesystems", produces = "application/json")
public class CodeSystemController {

	@Autowired
	private CodeSystemService codeSystemService;

	@ApiOperation(value = "Create a code system",
			notes = "Required fields are shortName and branch. " +
					"shortName should use format SNOMEDCT-XX where XX is the country code for national extensions. " +
					"defaultLanguageCode can be used to force the sort order of the languages listed under the codesystem, " +
					"otherwise these are sorted by the number of active translated terms.")
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> createCodeSystem(@RequestBody CodeSystemCreate codeSystem) {
		codeSystemService.createCodeSystem((CodeSystem) codeSystem);
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

	@ApiOperation("Update a code system")
	@RequestMapping(value = "/{shortName}", method = RequestMethod.PUT)
	@ResponseBody
	public CodeSystem updateCodeSystem(@PathVariable String shortName, @RequestBody CodeSystemUpdateRequest updateRequest) {
		CodeSystem codeSystem = findCodeSystem(shortName);
		codeSystemService.update(codeSystem, updateRequest);
		return findCodeSystem(shortName);
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

	@ApiOperation(value = "Upgrade code system to a different dependant version.",
			notes = "This operation can be used to upgrade an extension. The extension must have been imported on a branch which is a direct child of MAIN. " +
					"For example: MAIN/SNOMEDCT-BE. " +
					"An integrity check should be run after this operation to find content that needs fixing.")
	@RequestMapping(value = "/{shortName}/upgrade", method = RequestMethod.POST)
	@ResponseBody
	public void upgradeCodeSystem(@PathVariable String shortName, @RequestBody CodeSystemUpgradeRequest request) throws ServiceException {
		codeSystemService.upgrade(shortName, request.getNewDependantVersion());
	}

	@ApiOperation(value = "DEPRECATED - Migrate code system to a different dependant version.",
			notes = "DEPRECATED in favour of upgrade operation. " +
					"This operation is required when an extension exists under an International version branch, for example: MAIN/2019-01-31/SNOMEDCT-BE. " +
					"An integrity check should be run after this operation to find content that needs fixing.")
	@RequestMapping(value = "/{shortName}/migrate", method = RequestMethod.POST)
	@ResponseBody
	public void migrateCodeSystem(@PathVariable String shortName, @RequestBody CodeSystemMigrationRequest request) throws ServiceException {
		CodeSystem codeSystem = codeSystemService.find(shortName);
		ControllerHelper.throwIfNotFound("CodeSystem", codeSystem);

		codeSystemService.migrateDependantCodeSystemVersion(codeSystem, request.getDependantCodeSystem(), request.getNewDependantVersion(), request.isCopyMetadata());
	}

}
