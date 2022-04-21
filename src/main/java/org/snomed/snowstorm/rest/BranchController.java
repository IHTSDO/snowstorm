package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;

@RestController
@Tag(name = "Branching", description = "-")
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

	@Operation(summary = "Retrieve all branches")
	@GetMapping(value = "/branches")
	public List<Branch> retrieveAllBranches() {
		return clearMetadata(branchService.findAll());
	}
	
	@Operation(summary = "Retrieve branch descendants")
	@GetMapping(value = "/branches/{branch}/children")
	public List<Branch> retrieveBranchDescendants(
			@PathVariable String branch,
			@RequestParam(required = false, defaultValue = "false") boolean immediateChildren,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		ControllerHelper.validatePageSize(pageRequest.getOffset(), pageRequest.getPageSize());
		return clearMetadata(branchMergeService.findChildBranches(BranchPathUriUtil.decodePath(branch), immediateChildren, pageRequest));
	}

	@PostMapping(value = "/branches")
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

	@Operation(summary = "Replace all branch metadata")
	@PutMapping(value = "/branches/{branch}")
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

	@Operation(summary = "Upsert branch metadata",
			description = "The item or items in the request will be merged with the existing metadata.")
	@PutMapping(value = "/branches/{branch}/metadata-upsert")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public Map<String, Object> updateBranchMetadataItems(@PathVariable String branch, @RequestBody Map<String, Object> metadataToInsert) {
		branch = BranchPathUriUtil.decodePath(branch);
		final Branch latestBranch = branchService.findBranchOrThrow(branch);
		if (latestBranch.isLocked()) {
			throw new IllegalStateException("Branch metadata can not be updated when branch is locked.");
		}
		// Prevent updating internal values via REST api
		metadataToInsert.remove(INTERNAL_METADATA_KEY);
		Metadata newMetadata = latestBranch.getMetadata();
		newMetadata.putAll(metadataToInsert);
		return getBranchPojo(branchService.updateMetadata(branch, newMetadata)).getMetadata();
	}

	@Operation(summary = "Retrieve a single branch")
	@GetMapping(value = "/branches/{branch}")
	public BranchPojo retrieveBranch(@PathVariable String branch, @RequestParam(required = false, defaultValue = "false") boolean includeInheritedMetadata) {
		return getBranchPojo(branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branch), includeInheritedMetadata));
	}

	@PostMapping(value = "/branches/{branch}/actions/lock")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void lockBranch(@PathVariable String branch, @RequestParam String lockMessage) {
		branchService.lockBranch(BranchPathUriUtil.decodePath(branch), lockMessage);
	}

	@PostMapping(value = "/branches/{branch}/actions/unlock")
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

	@PostMapping(value = "/branches/{branchPath}/actions/set-author-flag")
	@PreAuthorize("hasPermission('AUTHOR', #branchPath)")
	public BranchPojo setAuthorFlag(@PathVariable String branchPath, @RequestBody SetAuthorFlag setAuthorFlag) {
		branchPath = BranchPathUriUtil.decodePath(branchPath);

		String name = setAuthorFlag.getName();
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Name for author flag not present");
		}

		return getBranchPojo(sBranchService.setAuthorFlag(branchPath, setAuthorFlag));
	}

	@PostMapping(value = "/reviews")
	@PreAuthorize("hasPermission('AUTHOR', #request.source) and hasPermission('AUTHOR', #request.target)")
	public ResponseEntity<Void> createBranchReview(@RequestBody @Valid CreateReviewRequest request) {
		BranchReview branchReview = reviewService.getCreateReview(request.getSource(), request.getTarget());
		final String id = branchReview.getId();
		return ControllerHelper.getCreatedResponse(id);
	}

	@GetMapping(value = "/reviews/{id}")
	public BranchReview getBranchReview(@PathVariable String id) {
		return ControllerHelper.throwIfNotFound("Branch review", reviewService.getBranchReview(id));
	}

	@GetMapping(value = "/reviews/{id}/concept-changes")
	public BranchReviewConceptChanges getBranchReviewConceptChanges(@PathVariable String id) {
		BranchReview branchReview = reviewService.getBranchReviewOrThrow(id);
		if (branchReview.getStatus() != ReviewStatus.CURRENT) {
			throw new IllegalStateException("Branch review status must be " + ReviewStatus.CURRENT + " but is " + branchReview.getStatus());
		}
		return new BranchReviewConceptChanges(branchReview.getChangedConcepts());
	}

	@PostMapping(value = "/merge-reviews")
	@PreAuthorize("hasPermission('AUTHOR', #request.source) and hasPermission('AUTHOR', #request.target)")
	public ResponseEntity<Void> createMergeReview(@RequestBody @Valid CreateReviewRequest request) {
		MergeReview mergeReview = reviewService.createMergeReview(request.getSource(), request.getTarget());
		return ControllerHelper.getCreatedResponse(mergeReview.getId());
	}

	@GetMapping(value = "/merge-reviews/{id}")
	public MergeReview getMergeReview(@PathVariable String id) {
		return ControllerHelper.throwIfNotFound("Merge review", reviewService.getMergeReview(id));
	}

	@GetMapping(value = "/merge-reviews/{id}/details")
	public Collection<MergeReviewConceptVersions> getMergeReviewConflictingConcepts(
			@PathVariable String id,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return reviewService.getMergeReviewConflictingConcepts(id, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@PostMapping(value = "/merge-reviews/{id}/{conceptId}")
	public void saveMergeReviewConflictingConcept(@PathVariable String id, @PathVariable Long conceptId, @RequestBody Concept manuallyMergedConcept) throws ServiceException {
		reviewService.persistManuallyMergedConcept(reviewService.getMergeReviewOrThrow(id), conceptId, manuallyMergedConcept);
	}

	@DeleteMapping(value = "/merge-reviews/{id}/{conceptId}")
	public void deleteMergeReviewConflictingConcept(@PathVariable String id, @PathVariable Long conceptId) {
		reviewService.persistManualMergeConceptDeletion(reviewService.getMergeReviewOrThrow(id), conceptId);
	}

	@PostMapping(value = "/merge-reviews/{id}/apply")
	public void applyMergeReview(@PathVariable String id) throws ServiceException {
		reviewService.applyMergeReview(reviewService.getMergeReviewOrThrow(id));
	}

	@Operation(summary = "Perform a branch rebase or promotion.",
			description = "The integrity-check endpoint should be used before performing a promotion to avoid promotion errors.")
	@PostMapping(value = "/merges")
	@PreAuthorize("hasPermission('AUTHOR', #mergeRequest.target)")
	public ResponseEntity<Void> mergeBranch(@RequestBody MergeRequest mergeRequest) {
		BranchMergeJob mergeJob = branchMergeService.mergeBranchAsync(mergeRequest);
		return ControllerHelper.getCreatedResponse(mergeJob.getId());
	}

	@GetMapping(value = "/merges/{mergeId}")
	public BranchMergeJob retrieveMerge(@PathVariable String mergeId) {
		return branchMergeService.getBranchMergeJobOrThrow(mergeId);
	}

	@Operation(summary = "Perform integrity check against changed components on this branch.",
			description = "Returns a report containing an entry for each type of issue found together with a map of components. " +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	@PostMapping(value = "/{branch}/integrity-check")
	public IntegrityIssueReport integrityCheck(@Parameter(description = "The branch path") @PathVariable(value = "branch") @NotNull final String branchPath) throws ServiceException {
		Branch branch = branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branchPath));
		return integrityService.findChangedComponentsWithBadIntegrityNotFixed(branch);
	}


	@PostMapping(value = "/{branch}/upgrade-integrity-check")
	@Operation(summary = "Perform integrity check against changed components during extension upgrade on the extension main branch and fix branch.",
			description = "Returns a report containing an entry for each type of issue found together with a map of components which still need to be fixed. " +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	public IntegrityIssueReport upgradeIntegrityCheck(
			@Parameter(description = "The fix branch path") @PathVariable(value = "branch") @NotNull final String fixBranchPath,
			@Parameter(description = "Extension main branch e.g MAIN/{Code System}") @RequestParam @NotNull String extensionMainBranchPath) throws ServiceException {
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

	@PostMapping(value = "/{branch}/integrity-check-full")
	@Operation(summary = "Perform integrity check against all components on this branch.",
			description = "Returns a report containing an entry for each type of issue found together with a map of components. " +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	public IntegrityIssueReport fullIntegrityCheck(@Parameter(description = "The branch path") @PathVariable(value = "branch") @NotNull final String branchPath) throws ServiceException {
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
