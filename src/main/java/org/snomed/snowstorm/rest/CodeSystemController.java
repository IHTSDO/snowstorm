package org.snomed.snowstorm.rest;

import com.google.common.base.Strings;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.fieldpermissions.CodeSystemCreate;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemUpgradeJob;
import org.snomed.snowstorm.dailybuild.DailyBuildService;
import org.snomed.snowstorm.extension.ExtensionAdditionalLanguageRefsetUpgradeService;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpdateRequest;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpgradeRequest;
import org.snomed.snowstorm.rest.pojo.CreateCodeSystemVersionRequest;
import org.snomed.snowstorm.rest.pojo.DependencyInfo;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;

import static java.lang.Boolean.TRUE;
import static org.snomed.snowstorm.core.data.services.CodeSystemService.SNOMEDCT;
import static org.snomed.snowstorm.rest.ImportController.CODE_SYSTEM_INTERNAL_RELEASE_FLAG_README;
import org.springframework.http.HttpStatus;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RestController
@Tag(name = "Code Systems", description = "-")
@RequestMapping(value = "/codesystems", produces = "application/json")
public class CodeSystemController {
	private static final Logger logger = LoggerFactory.getLogger(CodeSystemController.class);
	private static final String CODE_SYSTEM = "Code System";
	private final CodeSystemService codeSystemService;
	private final CodeSystemUpgradeService codeSystemUpgradeService;
	private final DailyBuildService dailyBuildService;
	private final PermissionService permissionService;
	private final ExtensionAdditionalLanguageRefsetUpgradeService extensionAdditionalLanguageRefsetUpgradeService;
	private final CodeSystemVersionService codeSystemVersionService;
	private final ModuleDependencyService moduleDependencyService;


	public CodeSystemController(CodeSystemService codeSystemService, CodeSystemUpgradeService codeSystemUpgradeService, DailyBuildService dailyBuildService, PermissionService permissionService, ExtensionAdditionalLanguageRefsetUpgradeService extensionAdditionalLanguageRefsetUpgradeService, CodeSystemVersionService codeSystemVersionService, ModuleDependencyService moduleDependencyService) {
		this.codeSystemService = codeSystemService;
		this.codeSystemUpgradeService = codeSystemUpgradeService;
		this.dailyBuildService = dailyBuildService;
		this.permissionService = permissionService;
		this.extensionAdditionalLanguageRefsetUpgradeService = extensionAdditionalLanguageRefsetUpgradeService;
		this.codeSystemVersionService = codeSystemVersionService;
		this.moduleDependencyService = moduleDependencyService;
	}

	@Value("${codesystem.all.latest-version.allow-future}")
	private boolean showFutureVersionsDefault;

	@Value("${codesystem.all.latest-version.allow-internal-release}")
	private boolean showInternalReleasesByDefault;

	@Operation(summary = "Create a code system",
			description = """
                    Required fields are shortName and branch.
                    shortName should use format SNOMEDCT-XX where XX is the country code for national extensions.
                    dependantVersion uses effectiveTime format and can be used if the new code system depends on an older version of the parent code system, otherwise the latest version will be selected automatically.
                    defaultLanguageCode can be used to force the sort order of the languages listed under the codesystem, otherwise these are sorted by the number of active translated terms.
                    maintainerType has no effect on API behaviour but can be used in frontend applications for extension categorisation.
                    defaultLanguageReferenceSet has no effect API behaviour but can be used by browsers to reflect extension preferences.\s""")
	@PostMapping
	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public ResponseEntity<Void> createCodeSystem(@RequestBody CodeSystemCreate codeSystem) {
		codeSystemService.createCodeSystem((CodeSystem) codeSystem);
		return ControllerHelper.getCreatedResponse(codeSystem.getShortName());
	}

	@Operation(summary = "List code systems",
			description = "List all code systems.\n" +
			"forBranch is an optional parameter to find the code system which the specified branch is within.")
	@GetMapping
	public ItemsPage<CodeSystem> listCodeSystems(@RequestParam(required = false) String forBranch) {
		if (!Strings.isNullOrEmpty(forBranch)) {
			CodeSystem codeSystem = codeSystemService.findClosestCodeSystemUsingAnyBranch(forBranch, true);
			if (codeSystem != null) {
				return new ItemsPage<>(Collections.singleton(joinUserPermissionsInfo(codeSystem)));
			} else {
				return new ItemsPage<>(Collections.emptySet());
			}
		} else {
			return new ItemsPage<>(joinUserPermissionsInfo(codeSystemService.findAll()));
		}
	}

	@Operation(summary = "Retrieve a code system")
	@GetMapping(value = "/{shortName}")
	public CodeSystem findCodeSystem(@PathVariable String shortName) {
		return joinUserPermissionsInfo(ControllerHelper.throwIfNotFound(CODE_SYSTEM, codeSystemService.find(shortName)));
	}

	@Operation(summary = "Update a code system")
	@PutMapping(value = "/{shortName}")
	public CodeSystem updateCodeSystem(@PathVariable String shortName, @RequestBody CodeSystemUpdateRequest updateRequest) {
		CodeSystem codeSystem = findCodeSystem(shortName);
		codeSystemService.update(codeSystem, updateRequest);
		return joinUserPermissionsInfo(findCodeSystem(shortName));
	}

	@Operation(summary = "Delete a code system", description = "This function deletes the code system and its versions but it does not delete the branches or the content.")
	@DeleteMapping(value = "/{shortName}")
	public void deleteCodeSystem(@PathVariable String shortName) {
		CodeSystem codeSystem = findCodeSystem(shortName);
		codeSystemService.deleteCodeSystemAndVersions(codeSystem);
	}

	@Operation(summary = "Retrieve versions of a code system")
	@GetMapping(value = "/{shortName}/versions")
	public ItemsPage<CodeSystemVersion> findAllVersions(
			@Parameter(description = "Code system short name.")
			@PathVariable String shortName,

			@Parameter(description = "Should versions with a future effective-time be shown.")
			@RequestParam(required = false) Boolean showFutureVersions,

			@Parameter(description = "Should versions marked as 'internalRelease' be shown.")
			@RequestParam(required = false) Boolean showInternalReleases) {

		if (showFutureVersions == null) {
			showFutureVersions = showFutureVersionsDefault;
		}
		if (showInternalReleases == null) {
			showInternalReleases = showInternalReleasesByDefault;
		}

		List<CodeSystemVersion> codeSystemVersions = codeSystemService.findAllVersions(shortName, showFutureVersions, showInternalReleases);
		for (CodeSystemVersion codeSystemVersion : codeSystemVersions) {
			codeSystemVersionService.populateDependantVersion(codeSystemVersion);
		}
		return new ItemsPage<>(codeSystemVersions);
	}

	@Operation(summary = "Create a new code system version",
			description = CODE_SYSTEM_INTERNAL_RELEASE_FLAG_README)
	@PostMapping(value = "/{shortName}/versions")
	public ResponseEntity<Void> createVersion(@PathVariable String shortName, @RequestBody CreateCodeSystemVersionRequest input) {
		CodeSystem codeSystem = codeSystemService.find(shortName);
		ControllerHelper.throwIfNotFound("CodeSystem", codeSystem);

		String versionId = codeSystemService.createVersion(codeSystem, input.getEffectiveDate(), input.getDescription(), input.isInternalRelease());
		return ControllerHelper.getCreatedResponse(versionId);
	}

	@Operation(summary = "Update the release package in an existing code system version",
			description = "This function is used to update the release package for a given version." +
					"The shortName is the code system short name e.g SNOMEDCT" +
					"The effectiveDate is the release date e.g 20210131" +
					"The releasePackage is the release zip file package name. e.g SnomedCT_InternationalRF2_PRODUCTION_20210131T120000Z.zip"
				)
	@PutMapping(value = "/{shortName}/versions/{effectiveDate}")
	public CodeSystemVersion updateVersion(@PathVariable String shortName, @PathVariable Integer effectiveDate, @RequestParam String releasePackage) {
		// release package and effective date checking
		ControllerHelper.requiredParam(releasePackage, "releasePackage");
		if (!releasePackage.endsWith(".zip")) {
			throw new IllegalArgumentException(String.format("The release package %s is not a zip filename.", releasePackage));
		}
		// Check CodeSystemVersion exists or not
		CodeSystem codeSystem = codeSystemService.find(shortName);
		ControllerHelper.throwIfNotFound("CodeSystem", codeSystem);

		CodeSystemVersion codeSystemVersion = codeSystemService.findVersion(shortName, effectiveDate);
		ControllerHelper.throwIfNotFound("CodeSystemVersion", codeSystemVersion);
		return codeSystemService.updateCodeSystemVersionPackage(codeSystemVersion, releasePackage);
	}

	@Operation(summary = "Upgrade code system to a different dependant version.",
			description = """
                    This operation can be used to upgrade an extension to a new version of the parent code system.
                    If daily build is enabled for this code system that will be temporarily disabled and the daily build content will be rolled back automatically.
                    The extension must have been imported on a branch which is a direct child of MAIN.
                    For example: MAIN/SNOMEDCT-BE.
                    _newDependantVersion_ uses the same format as the effectiveTime RF2 field, for example '20190731'.
                    _contentAutomations_ should be set to false unless you are the extension maintainer and would like some automatic content changes made to support creating a new version of the extension.
                    If you are the extension maintainer an integrity check should be run after this operation to find content that needs fixing.""")
	@PostMapping(value = "/{shortName}/upgrade")
	public ResponseEntity<Void> upgradeCodeSystem(@PathVariable String shortName, @RequestBody CodeSystemUpgradeRequest request) throws ServiceException {
		CodeSystem codeSystem = codeSystemService.findOrThrow(shortName);
		String jobId = codeSystemUpgradeService.upgradeAsync(codeSystem, request.getNewDependantVersion(), TRUE.equals(request.getContentAutomations()));
		return ControllerHelper.getCreatedResponse(jobId, "/" + shortName);
	}

	@Operation(summary = "Retrieve an upgrade job.",
			description = "Retrieves the state of an upgrade job. Used to view the upgrade configuration and check its status.")
	@GetMapping(value = "/upgrade/{jobId}")
	public CodeSystemUpgradeJob getUpgradeJob(@PathVariable String jobId) {
		return codeSystemUpgradeService.getUpgradeJobOrThrow(jobId);
	}

	@Operation(summary = "Check if daily build import matches today's date.")
	@GetMapping(value = "/{shortName}/daily-build/check")
	public boolean getLatestDailyBuild(@PathVariable String shortName) {
		return dailyBuildService.hasLatestDailyBuild(shortName);
	}

	@Operation(summary = "Rollback daily build commits.",
			description = "If you have a daily build set up for a code system this operation should be used to revert/rollback the daily build content " +
					"before importing any versioned content. Be sure to disable the daily build too.")
	@PostMapping(value = "/{shortName}/daily-build/rollback")
	public void rollbackDailyBuildContent(@PathVariable String shortName) {
		CodeSystem codeSystem = codeSystemService.find(shortName);
		dailyBuildService.rollbackDailyBuildContent(codeSystem);
	}

	@Operation(summary = "Trigger scheduled daily build import.",
		description = "The daily build import is scheduled to perform at a configured time interval per default." +
				"This operation manually triggers the scheduled daily build import service to perform.")
	@RequestMapping(value = "/{shortName}/daily-build/import", method = RequestMethod.POST)
	public void triggerScheduledImport(@PathVariable String shortName) {
			CodeSystem codeSystem = codeSystemService.find(shortName);
			dailyBuildService.triggerScheduledImport(codeSystem);
	}


	@Operation(summary = "Generate additional english language refset for certain extensions (IE or NZ) by copying international en-gb language refsets into extension module",
			description = "Before running this the extension must be upgraded already. " +
					"You must specify a task branch path (e.g MAIN/SNOMEDCT-NZ/{project}/{task}) for the delta to be created in. " +
					"Set completeCopy flag to true when creating extension for the first time. It will copy all active en-gb language refset components into extension module. " +
					"Set completeCopy flag to false for subsequent upgrades. Recent changes only from international release will be copied/updated in extension module. " +
					"It works for both incremental monthly upgrade and roll-up upgrade (e.g every 6 months). " +
					"Currently you should only run this api when upgrading SNOMEDCT-IE and SNOMEDCT-NZ")
	@PostMapping(value = "/{shortName}/additional-en-language-refset-delta")
	public void generateAdditionalLanguageRefsetDelta(@PathVariable String shortName,
													  @RequestParam String branchPath,
													  @Parameter(description = "The language refset to copy from e.g 900000000000508004 | Great Britain English language reference set (foundation metadata concept) ")
													  @RequestParam (defaultValue = "900000000000508004") String languageRefsetToCopyFrom,
													  @Parameter(description = "Set completeCopy to true to copy all active components and false to copy changes only from recent international release.")
													  @RequestParam (defaultValue = "false") Boolean completeCopy) {

		ControllerHelper.requiredParam(shortName, "shortName");
		CodeSystem codeSystem = codeSystemService.find(shortName);
		if (codeSystem == null) {
			throw new NotFoundException("No code system found with short name " + shortName);
		}
		if (!BranchPathUriUtil.decodePath(branchPath).contains(codeSystem.getBranchPath())) {
			throw new IllegalArgumentException(String.format("Given branch %s must the code system branch %s or a child branch.", BranchPathUriUtil.decodePath(branchPath), codeSystem.getBranchPath()));
		}
		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(codeSystem, BranchPathUriUtil.decodePath(branchPath), languageRefsetToCopyFrom, completeCopy);
	}

	@Operation(summary = "Clear cache of code system calculated/aggregated information.")
	@PostMapping(value = "/clear-cache")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void clearCodeSystemInformationCache() {
		codeSystemService.clearCache();
		codeSystemVersionService.clearCache();
	}

	@Operation(summary = "Update details from config. For each existing Code System the name, country code and owner are set using the values in configuration.")
	@PostMapping(value = "/update-details-from-config")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void updateDetailsFromConfig() {
		codeSystemService.updateDetailsFromConfig();
	}


	@Operation(summary = "Start new authoring cycle for given code system")
	@PostMapping(value = "/{shortName}/new-authoring-cycle")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void startNewAuthoringCycle(@PathVariable String shortName, @RequestParam(required = false) String newEffectiveTime) {
		CodeSystem codeSystem = ControllerHelper.throwIfNotFound(CODE_SYSTEM, codeSystemService.find(shortName));
		codeSystemService.updateCodeSystemBranchMetadata(codeSystem);
		moduleDependencyService.clearSourceAndTargetEffectiveTimes(codeSystem.getBranchPath());
		codeSystemService.notifyCodeSystemNewAuthoringCycle(codeSystem, newEffectiveTime);
	}

	@Operation(summary = "Get compatible dependent versions for code system dependencies",
			description = """
			Retrieves all compatible versions for dependencies of the specified code system, using the current 
			dependent version as the baseline for compatibility checking.
			
			**Functionality:**
			- Returns versions that are compatible across all current dependencies
			- If additional code systems are specified via the 'with' parameter, includes them in compatibility checking
			- Always includes the current version if it remains compatible
			- Uses the findCompatibleVersions logic to determine version compatibility
			
			**Parameters:**
			- shortName: The code system to check dependencies for
			- with: Optional comma-separated list of additional code systems to include in compatibility checking
			
			**Restrictions:**
			- The main code system (shortName) must not be SNOMEDCT
			- Any additional code systems in the 'with' parameter must not be SNOMEDCT
			
			**Response:**
			- Returns a map with 'compatibleVersions' key containing a list of compatible version strings
			- Versions are returned in a format suitable for dependency management
			""")
	@GetMapping(value = "/{shortName}/dependencies/compatible-versions")
	public ResponseEntity<Map<String, List<String>>> getCompatibleDependentVersions(
			@PathVariable String shortName,
			@RequestParam(required = false) String with) {
		
		CodeSystem currentCodeSystem = findCodeSystemOrThrow(shortName);
		// Parse and validate additional code systems if provided
		List<CodeSystem> additionalCodeSystems = parseAndValidateCodeSystems(with);
		if (SNOMEDCT.equalsIgnoreCase(shortName) || additionalCodeSystems.stream().anyMatch(cs -> SNOMEDCT.equalsIgnoreCase(cs.getShortName()))) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", List.of("SNOMEDCT cannot be used as an additional dependency or as the extension code system.")));
		}

		// Get current dependent version
		Integer currentDependantVersion = currentCodeSystem.getDependantVersionEffectiveTime();
		if (currentDependantVersion == null) {
			logger.warn("No dependent version found for '{}'", shortName);
			Map<String, List<String>> response = new HashMap<>();
			response.put("compatibleVersions", Collections.emptyList());
			return ResponseEntity.ok(response);
		}

		// Get all dependencies to check (current + additional)
		Set<CodeSystem> allDependenciesToCheck = buildCompleteDependencySet(currentCodeSystem, additionalCodeSystems);
		
		// Find compatible versions across all dependencies
		List<Integer> compatibleVersions = codeSystemVersionService.findCompatibleVersions(allDependenciesToCheck, currentDependantVersion);
		
		Map<String, List<String>> response = new HashMap<>();
		response.put("compatibleVersions", compatibleVersions.stream().map(String::valueOf).toList());
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Add a code system dependency to the current code system",
			description = """
			Adds a single additional code system as a dependency to the specified code system by creating 
			Module Dependency Reference Set (MDRS) entries that establish the dependency relationship.
			
			**Functionality:**
			- Creates MDRS entries linking the current code system to the additional dependency
			- Uses the holdingModule to establish the dependency relationship
			- Validates compatibility between the current and additional code systems
			- Prevents duplicate dependencies from being added
			
			**Parameters:**
			- shortName: The code system to add the dependency to
			- holdingModule: The module ID that will link the current code system with the new dependency
			- with: The short name of the code system to add as a dependency (single code system only)
			
			**Business Rules:**
			- The main code system (shortName) must not be SNOMEDCT
			- The additional code system (with) must not be SNOMEDCT
			- The additional code system must not already be a dependency
			- The additional code system must exist and be valid
			- Compatibility is checked before adding the dependency
			
			**Response:**
			- 201 Created: Dependency successfully added
			- 400 Bad Request: Invalid parameters, duplicate dependency, or business rule violation
			- 404 Not Found: Code system not found
			- 500 Internal Server Error: Unexpected error during processing
			
			**MDRS Creation:**
			- Creates ReferenceSetMember entries with MODULE_DEPENDENCY_REFERENCE_SET refsetId
			- Sets TARGET_EFFECTIVE_TIME based on the additional code system's version
			- Links the holdingModule to the dependency's default module
			""")
	@PostMapping(value = "/{shortName}/dependencies")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public ResponseEntity<String> addAdditionalCodeSystemDependency (
			@PathVariable String shortName,
			@RequestParam String holdingModule,
			@RequestParam String with) {
		
		CodeSystem currentCodeSystem = findCodeSystemOrThrow(shortName);

		if (with == null || with.trim().isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\": \"Invalid code system: " + with + "\"}");
		}
		CodeSystem additionalCodeSystem = findCodeSystemOrThrow(with);
		if (SNOMEDCT.equalsIgnoreCase(shortName) || SNOMEDCT.equalsIgnoreCase(additionalCodeSystem.getShortName())) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("{\"error\": \"SNOMEDCT cannot be used as an additional dependency or as the extension code system.\"}");
		}

		// Check whether additional dependency exists already
		if (moduleDependencyService.getAllDependentCodeSystems(currentCodeSystem).contains(additionalCodeSystem)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("{\"error\": \"Additional code system dependency cannot be added because it exists already.\"}");
		}
		// Check compatibility with current dependent version
		Integer currentDependantVersion = currentCodeSystem.getDependantVersionEffectiveTime();
		if (currentDependantVersion == null) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body("{\"error\": \"Cannot add any additional code system as current code system " + currentCodeSystem.getShortName() + " has no dependent version set\"}");
		}
		
		// Get all dependencies to check (current + new)
		Set<CodeSystem> allDependenciesToCheck = buildAdditionalDependencySet(currentCodeSystem, List.of(additionalCodeSystem));

		// Find compatible versions and validate current version is compatible
		List<Integer> compatibleVersions = codeSystemVersionService.findCompatibleVersions(allDependenciesToCheck, currentDependantVersion);
		if (!compatibleVersions.contains(currentDependantVersion)) {
			String errorMsg = String.format("Cannot add additional code system %s because no compatible releases were found with current dependent version (%s).",
					additionalCodeSystem.getShortName(), currentDependantVersion);
			if (!compatibleVersions.isEmpty()) {
				errorMsg += String.format(" Upgrade %s to %s and try again.", currentCodeSystem.getShortName(), compatibleVersions);
			}
			return ResponseEntity.status(HttpStatus.CONFLICT).body("{\"error\": \"" + errorMsg + "\"}");
		}
		
		// Create MDRS entries for additional dependency
		try {
			moduleDependencyService.createMDRSEntriesForAdditionalDependency(holdingModule, currentCodeSystem, additionalCodeSystem, currentDependantVersion);
		} catch (Exception e) {
			logger.error("Failed to create MDRS entries for new dependencies: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("{\"error\": \"Failed to add additional dependency on : " + additionalCodeSystem.getShortName() + " with error: " + e.getMessage() + "\"}");
		}
		return ResponseEntity.status(HttpStatus.CREATED).body("{\"message\": \"Additional dependency added successfully: " + additionalCodeSystem.getShortName() + "\"}");
	}


	@Operation(summary = "Get all dependencies for a code system",
			description = """
			Retrieves all existing dependencies for the specified code system, including their version information.
			This endpoint provides a complete view of what other code systems the specified code system depends on,
			which is essential for understanding dependency relationships and planning upgrades.
			
			**Functionality:**
			- Queries Module Dependency Reference Set (MDRS) entries to find all dependencies
			- Retrieves version information from MDRS TARGET_EFFECTIVE_TIME field
			- Returns dependency information in a structured format
			- Handles special cases for SNOMEDCT (root code system with no dependencies)
			
			**Parameters:**
			
			**Response Format:**
			Returns an array of dependency objects, each containing:
			- `codeSystem`: The short name of the dependent code system
			- `version`: The version/effective time of the dependency (or "Unknown" if not available)
			
			**Special Cases:**
			- **SNOMEDCT**: Returns an empty array `[]` as it is the root code system with no dependencies
			- **Non-existent code systems**: Returns HTTP 404 Not Found
			- **System errors**: Returns HTTP 500 Internal Server Error
			
			**Example Response:**
			**Example Responses:**
			```json
			[
			  {
			    "codeSystem": "SNOMEDCT-LOINC",
			    "version": "20250901"
			  },
			  {
			    "codeSystem": "SNOMEDCT-ICD10",
			    "version": "20250801"
			  }
			]
			```
			""")
	@GetMapping(value = "/{shortName}/dependencies")
	public ResponseEntity<List<DependencyInfo>> getAllDependencies(@PathVariable String shortName) {
		CodeSystem currentCodeSystem = findCodeSystemOrThrow(shortName);
		
		// SNOMEDCT has no dependencies, return empty list
		if (SNOMEDCT.equalsIgnoreCase(shortName)) {
			return ResponseEntity.ok(Collections.emptyList());
		}

		try {
			// Get all dependencies
			List<DependencyInfo> dependencies = moduleDependencyService.getAllDependencies(currentCodeSystem);
			return ResponseEntity.ok(dependencies);
		} catch (Exception e) {
			logger.error("Failed to retrieve dependencies for code system {}: {}", shortName, e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}


	/**
	 * Finds a code system by short name or throws NotFoundException if not found.
	 */
	private CodeSystem findCodeSystemOrThrow(String shortName) {
		return ControllerHelper.throwIfNotFound(CODE_SYSTEM, codeSystemService.find(shortName));
	}

	/**
	 * Parses a comma-separated string of code system short names and validates they exist.
	 * Returns an empty list if the input is null or empty.
	 */
	private List<CodeSystem> parseAndValidateCodeSystems(String codeSystemsString) {
		if (codeSystemsString == null || codeSystemsString.trim().isEmpty()) {
			return Collections.emptyList();
		}
		
		List<String> shortNames = Arrays.stream(codeSystemsString.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
		
		if (shortNames.isEmpty()) {
			return Collections.emptyList();
		}
		
		List<CodeSystem> codeSystems = new ArrayList<>();
		for (String shortName : shortNames) {
			CodeSystem codeSystem = codeSystemService.find(shortName);
			if (codeSystem == null) {
				throw new NotFoundException("Code system not found: " + shortName);
			}
			codeSystems.add(codeSystem);
		}
		
		return codeSystems;
	}

	/**
	 * Builds a complete set of dependencies by combining current dependencies with additional ones.
	 */
	private Set<CodeSystem> buildCompleteDependencySet(CodeSystem currentCodeSystem, List<CodeSystem> additionalCodeSystems) {
		// Get current dependencies including SNOMEDCT
		Set<CodeSystem> currentDependencies = moduleDependencyService.getAllDependentCodeSystems(currentCodeSystem);
		// Combine current dependencies with additional code systems
		currentDependencies.addAll(additionalCodeSystems);
		return currentDependencies;
	}

	/**
	 * Builds a set of additional dependencies only by combining current dependencies with additional ones.
	 */
	private Set<CodeSystem> buildAdditionalDependencySet(CodeSystem currentCodeSystem, List<CodeSystem> additionalCodeSystems) {
		// Get current dependencies excluding SNOMEDCT
		Set<CodeSystem> currentDependencies = moduleDependencyService.getAllDependentCodeSystems(currentCodeSystem)
				.stream()
				.filter(dependency -> !SNOMEDCT.equals(dependency.getShortName()))
				.collect(Collectors.toSet());

		// Combine current dependencies with additional code systems
		currentDependencies.addAll(additionalCodeSystems);
		return currentDependencies;
	}

	private CodeSystem joinUserPermissionsInfo(CodeSystem codeSystem) {
		joinUserPermissionsInfo(Collections.singleton(codeSystem));
		return codeSystem;
	}

	private Collection<CodeSystem> joinUserPermissionsInfo(Collection<CodeSystem> codeSystems) {
		for (CodeSystem codeSystem : codeSystems) {
			final String branchPath = codeSystem.getBranchPath();
			if (branchPath != null) {
				codeSystem.setUserRoles(permissionService.getUserRolesForBranch(branchPath).getGrantedBranchRole());
				CodeSystemVersion latestVersion = codeSystem.getLatestVersion();
				if (latestVersion == null) {
					continue;
				}
				codeSystemVersionService.populateDependantVersion(latestVersion);
			}
		}
		return codeSystems;
	}
}
