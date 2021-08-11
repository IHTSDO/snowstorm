package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.BranchMergeJob;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.review.BranchReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReviewConceptVersions;
import org.snomed.snowstorm.core.data.domain.review.ReviewStatus;
import org.snomed.snowstorm.core.data.domain.security.UserBranchRoles;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.classification.BranchClassificationStatusService;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.rest.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.*;

import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.AUTHOR_FLAGS_METADATA_KEY;
import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;

@RestController
@Api(tags = "Branching", description = "-")
@RequestMapping(produces = "application/json")
public class BranchController {

	@Autowired
	private BranchService branchService;

	@Autowired
	private SBranchService sBranchService;

	@Autowired
	private BranchReviewService reviewService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private IntegrityService integrityService;

	@Autowired
	private PermissionService permissionService;

	@Value("${snowstorm.rest-api.readonly}")
	private boolean restApiReadOnly;

	@ApiOperation("Retrieve all branches")
	@RequestMapping(value = "/branches", method = RequestMethod.GET)
	public List<Branch> retrieveAllBranches() {
		return clearMetadata(branchService.findAll());
	}
	
	@ApiOperation("Retrieve branch descendants")
	@RequestMapping(value = "/branches/{branch}/children", method = RequestMethod.GET)
	public List<Branch> retrieveBranchDescendants(
			@PathVariable String branch,
			@RequestParam(required = false, defaultValue = "false") boolean immediateChildren,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		ControllerHelper.validatePageSize(pageRequest.getOffset(), pageRequest.getPageSize());
		return clearMetadata(branchMergeService.findChildBranches(BranchPathUriUtil.decodePath(branch), immediateChildren, pageRequest));
	}

	@RequestMapping(value = "/branches", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #request.branch)")
	public BranchPojo createBranch(@RequestBody CreateBranchRequest request) {
		return getBranchPojo(sBranchService.create(request.getBranch(), request.getMetadata()));
	}

	private BranchPojo getBranchPojo(Branch branch) {
		final UserBranchRoles userRolesForBranch = permissionService.getUserRolesForBranch(branch.getPath());
		if (restApiReadOnly) {
			BranchClassificationStatusService.clearClassificationStatus(branch.getMetadata());
		}
		return new BranchPojo(branch, branch.getMetadata().getAsMap(), userRolesForBranch);
	}

	@ApiOperation("Update branch metadata")
	@RequestMapping(value = "/branches/{branch}", method = RequestMethod.PUT)
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public BranchPojo updateBranch(@PathVariable String branch, @RequestBody UpdateBranchRequest request) {
		branch = BranchPathUriUtil.decodePath(branch);
		final Branch latestBranch = branchService.findBranchOrThrow(branch);
		if (latestBranch.isLocked()) {
			throw new IllegalStateException("Branch metadata can not be updated when branch is locked.");
		}
		Metadata newMetadata = new Metadata(request.getMetadata());
		// Prevent updating internal values via REST api
		newMetadata.remove(INTERNAL_METADATA_KEY);
		newMetadata.putMap(INTERNAL_METADATA_KEY, latestBranch.getMetadata().getMapOrCreate(INTERNAL_METADATA_KEY));
		return getBranchPojo(branchService.updateMetadata(branch, newMetadata));
	}

	@ApiOperation("Retrieve a single branch")
	@RequestMapping(value = "/branches/{branch}", method = RequestMethod.GET)
	public BranchPojo retrieveBranch(@PathVariable String branch, @RequestParam(required = false, defaultValue = "false") boolean includeInheritedMetadata) {
		return getBranchPojo(branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branch), includeInheritedMetadata));
	}

	@RequestMapping(value = "/branches/{branch}/actions/lock", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void lockBranch(@PathVariable String branch, @RequestParam String lockMessage) {
		branchService.lockBranch(BranchPathUriUtil.decodePath(branch), lockMessage);
	}

	@RequestMapping(value = "/branches/{branch}/actions/unlock", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void unlockBranch(@PathVariable String branch) {
		branch = BranchPathUriUtil.decodePath(branch);
		Date partialCommitTimestamp = sBranchService.getPartialCommitTimestamp(branch);
		if (partialCommitTimestamp != null) {
			throw new IllegalStateException("There is a partial commit on this branch. " +
					"Please wait for the commit to complete, or if you are sure that it has failed use the rollback partial commit admin function.");
		}
		branchService.unlock(branch);
	}

	@RequestMapping(value = "/branches/{branchPath}/actions/set-author-flag", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #branchPath)")
	public BranchPojo setAuthorFlag(@PathVariable String branchPath, @RequestBody SetAuthorFlag setAuthorFlag) {
		branchPath = BranchPathUriUtil.decodePath(branchPath);
		Branch branch = branchService.findBranchOrThrow(branchPath);

		String name = setAuthorFlag.getName();
		String value = String.valueOf(setAuthorFlag.isValue());
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Name for author flag not present");
		}

		Metadata metadata = branch.getMetadata();
		Map<String, String> authFlagMap = metadata.getMapOrCreate(AUTHOR_FLAGS_METADATA_KEY);
		authFlagMap.put(name, value);
		metadata.putMap(AUTHOR_FLAGS_METADATA_KEY, authFlagMap);

		return getBranchPojo(branchService.updateMetadata(branchPath, metadata));
	}

	@RequestMapping(value = "/reviews", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #request.source) and hasPermission('AUTHOR', #request.target)")
	public ResponseEntity<Void> createBranchReview(@RequestBody @Valid CreateReviewRequest request) {
		BranchReview branchReview = reviewService.getCreateReview(request.getSource(), request.getTarget());
		final String id = branchReview.getId();
		return ControllerHelper.getCreatedResponse(id);
	}

	@RequestMapping(value = "/reviews/{id}", method = RequestMethod.GET)
	public BranchReview getBranchReview(@PathVariable String id) {
		return ControllerHelper.throwIfNotFound("Branch review", reviewService.getBranchReview(id));
	}

	@RequestMapping(value = "/reviews/{id}/concept-changes", method = RequestMethod.GET)
	public BranchReviewConceptChanges getBranchReviewConceptChanges(@PathVariable String id) {
		BranchReview branchReview = reviewService.getBranchReviewOrThrow(id);
		if (branchReview.getStatus() != ReviewStatus.CURRENT) {
			throw new IllegalStateException("Branch review status must be " + ReviewStatus.CURRENT + " but is " + branchReview.getStatus());
		}
		return new BranchReviewConceptChanges(branchReview.getChangedConcepts());
	}

	@RequestMapping(value = "/merge-reviews", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #request.source) and hasPermission('AUTHOR', #request.target)")
	public ResponseEntity<Void> createMergeReview(@RequestBody @Valid CreateReviewRequest request) {
		MergeReview mergeReview = reviewService.createMergeReview(request.getSource(), request.getTarget());
		return ControllerHelper.getCreatedResponse(mergeReview.getId());
	}

	@RequestMapping(value = "/merge-reviews/{id}", method = RequestMethod.GET)
	public MergeReview getMergeReview(@PathVariable String id) {
		return ControllerHelper.throwIfNotFound("Merge review", reviewService.getMergeReview(id));
	}

	@RequestMapping(value = "/merge-reviews/{id}/details", method = RequestMethod.GET)
	public Collection<MergeReviewConceptVersions> getMergeReviewConflictingConcepts(
			@PathVariable String id,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return reviewService.getMergeReviewConflictingConcepts(id, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@RequestMapping(value = "/merge-reviews/{id}/{conceptId}", method = RequestMethod.POST)
	public void saveMergeReviewConflictingConcept(@PathVariable String id, @PathVariable Long conceptId, @RequestBody Concept manuallyMergedConcept) throws ServiceException {
		reviewService.persistManuallyMergedConcept(reviewService.getMergeReviewOrThrow(id), conceptId, manuallyMergedConcept);
	}

	@RequestMapping(value = "/merge-reviews/{id}/{conceptId}", method = RequestMethod.DELETE)
	public void deleteMergeReviewConflictingConcept(@PathVariable String id, @PathVariable Long conceptId) {
		reviewService.persistManualMergeConceptDeletion(reviewService.getMergeReviewOrThrow(id), conceptId);
	}

	@RequestMapping(value = "/merge-reviews/{id}/apply", method = RequestMethod.POST)
	public void applyMergeReview(@PathVariable String id) throws ServiceException {
		reviewService.applyMergeReview(reviewService.getMergeReviewOrThrow(id));
	}

	@ApiOperation(value = "Perform a branch rebase or promotion.",
			notes = "The integrity-check endpoint should be used before performing a promotion to avoid promotion errors.")
	@RequestMapping(value = "/merges", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #mergeRequest.target)")
	public ResponseEntity<Void> mergeBranch(@RequestBody MergeRequest mergeRequest) {
		BranchMergeJob mergeJob = branchMergeService.mergeBranchAsync(mergeRequest);
		return ControllerHelper.getCreatedResponse(mergeJob.getId());
	}

	@RequestMapping(value = "/merges/{mergeId}", method = RequestMethod.GET)
	public BranchMergeJob retrieveMerge(@PathVariable String mergeId) {
		return branchMergeService.getBranchMergeJobOrThrow(mergeId);
	}

	@ApiOperation(value = "Perform integrity check against changed components on this branch.",
			notes = "Returns a report containing an entry for each type of issue found together with a map of components. " +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	@RequestMapping(value = "/{branch}/integrity-check", method = RequestMethod.POST)
	public IntegrityIssueReport integrityCheck(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull final String branchPath) throws ServiceException {
		Branch branch = branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branchPath));
		return integrityService.findChangedComponentsWithBadIntegrityNotFixed(branch);
	}


	@RequestMapping(value = "/{branch}/upgrade-integrity-check", method = RequestMethod.POST)
	@ApiOperation(value = "Perform integrity check against changed components during extension upgrade on the extension main branch and fix branch.",
			notes = "Returns a report containing an entry for each type of issue found together with a map of components which still need to be fixed. " +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	public IntegrityIssueReport upgradeIntegrityCheck(
			@ApiParam(value="The fix branch path") @PathVariable(value="branch") @NotNull final String fixBranchPath,
			@ApiParam(value="Extension main branch e.g MAIN/{Code System}") @RequestParam @NotNull String extensionMainBranchPath) throws ServiceException {
		Branch branch = branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(fixBranchPath));
		if ("MAIN".equalsIgnoreCase(extensionMainBranchPath)) {
			throw new IllegalArgumentException("Extension main branch path can't be MAIN");
		}
		Branch extensionMainBranch = branchService.findBranchOrThrow(extensionMainBranchPath);
		if (!"MAIN".equalsIgnoreCase(PathUtil.getParentPath(extensionMainBranch.getPath()))) {
			throw new IllegalArgumentException("The parent of an extension main branch must be MAIN but is " + PathUtil.getParentPath(extensionMainBranch.getPath()));
		}
		// check task is a descendant of extension main
		if (!branch.getPath().startsWith(extensionMainBranchPath)) {
			throw new IllegalArgumentException(String.format("Branch %s is not a descendant of %s", branch.getPath(), extensionMainBranchPath));
		}
		return integrityService.findChangedComponentsWithBadIntegrityNotFixed(branch, extensionMainBranchPath);
	}

	@RequestMapping(value = "/{branch}/integrity-check-full", method = RequestMethod.POST)
	@ApiOperation(value = "Perform integrity check against all components on this branch.",
			notes = "Returns a report containing an entry for each type of issue found together with a map of components. " +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	public IntegrityIssueReport fullIntegrityCheck(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull final String branchPath) throws ServiceException {
		Branch branch = branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branchPath));
		return integrityService.findAllComponentsWithBadIntegrity(branch, true);
	}

	private List<Branch> clearMetadata(List<Branch> allBranches) {
		for (Branch branch : allBranches) {
			branch.setMetadata(null);
		}
		return allBranches;
	}

}
