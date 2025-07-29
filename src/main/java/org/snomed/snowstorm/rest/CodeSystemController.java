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
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemUpgradeJob;
import org.snomed.snowstorm.dailybuild.DailyBuildService;
import org.snomed.snowstorm.extension.ExtensionAdditionalLanguageRefsetUpgradeService;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpdateRequest;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpgradeRequest;
import org.snomed.snowstorm.rest.pojo.CreateCodeSystemVersionRequest;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.lang.Boolean.TRUE;
import static org.snomed.snowstorm.rest.ImportController.CODE_SYSTEM_INTERNAL_RELEASE_FLAG_README;

@RestController
@Tag(name = "Code Systems", description = "-")
@RequestMapping(value = "/codesystems", produces = "application/json")
public class CodeSystemController {
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
		return joinUserPermissionsInfo(ControllerHelper.throwIfNotFound("Code System", codeSystemService.find(shortName)));
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
		CodeSystem codeSystem = ControllerHelper.throwIfNotFound("Code System", codeSystemService.find(shortName));
		codeSystemService.updateCodeSystemBranchMetadata(codeSystem);
		moduleDependencyService.clearSourceAndTargetEffectiveTimes(codeSystem.getBranchPath());
		codeSystemService.notifyCodeSystemNewAuthoringCycle(codeSystem, newEffectiveTime);
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
