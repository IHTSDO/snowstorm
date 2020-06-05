package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
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
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.rest.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.websocket.server.PathParam;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;

@RestController
@Api(tags = "Branching", description = "-")
@RequestMapping(produces = "application/json")
public class BranchController {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private BranchReviewService reviewService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private IntegrityService integrityService;

	@ApiOperation("Retrieve all branches")
	@RequestMapping(value = "/branches", method = RequestMethod.GET)
	@ResponseBody
	public List<Branch> retrieveAllBranches() {
		return clearMetadata(branchService.findAll());
	}
	
	@ApiOperation("Retrieve branch descendants")
	@RequestMapping(value = "/branches/{path}/children", method = RequestMethod.GET)
	@ResponseBody
	public List<Branch> retrieveBranchDescendants(
			@PathVariable String path,
			@RequestParam(required = false, defaultValue = "false") boolean immediateChildren,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		ControllerHelper.validatePageSize(pageRequest.getOffset(), pageRequest.getPageSize());
		return clearMetadata(branchMergeService.findChildBranches(BranchPathUriUtil.decodePath(path), immediateChildren, pageRequest));
	}

	@RequestMapping(value = "/branches", method = RequestMethod.POST)
	@ResponseBody
	public BranchPojo createBranch(@RequestBody CreateBranchRequest request) {
		Map<String, String> flatMetadata = branchMetadataHelper.flattenObjectValues(request.getMetadata());
		return getBranchPojo(branchService.create(request.getParent() + "/" + request.getName(), flatMetadata));
	}

	private BranchPojo getBranchPojo(Branch branch) {
		return new BranchPojo(branch, branchMetadataHelper.expandObjectValues(branch.getMetadata()));
	}

	@ApiOperation("Update branch metadata")
	@RequestMapping(value = "/branches/{path}", method = RequestMethod.PUT)
	@ResponseBody
	public BranchPojo updateBranch(@PathVariable String path, @RequestBody UpdateBranchRequest request) {
		path = BranchPathUriUtil.decodePath(path);
		if (branchService.findBranchOrThrow(path).isLocked()) {
			throw new IllegalStateException("Branch metadata can not be updated when branch is locked.");
		}
		Branch branch = branchService.findBranchOrThrow(path);
		Map<String, String> metadata = branchMetadataHelper.flattenObjectValues(request.getMetadata());
		// skip updating for internal via REST api
		if (metadata.containsKey(INTERNAL_METADATA_KEY)) {
			metadata.remove(INTERNAL_METADATA_KEY);
		}
		Map<String, String> existing = branch.getMetadata();
		if (existing.containsKey(INTERNAL_METADATA_KEY)) {
			metadata.put(INTERNAL_METADATA_KEY, existing.get(INTERNAL_METADATA_KEY));
		}
		return getBranchPojo(branchService.updateMetadata(path, metadata));
	}

	@ApiOperation("Retrieve a single branch")
	@RequestMapping(value = "/branches/{path}", method = RequestMethod.GET)
	@ResponseBody
	public BranchPojo retrieveBranch(@PathVariable String path, @RequestParam(required = false, defaultValue = "false") boolean includeInheritedMetadata) {
		return getBranchPojo(branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(path), includeInheritedMetadata));
	}

	@RequestMapping(value = "/branches/{path}/actions/lock", method = RequestMethod.POST)
	@ResponseBody
	public void lockBranch(@PathVariable String path, @RequestParam String lockMessage) {
		branchService.lockBranch(BranchPathUriUtil.decodePath(path), lockMessage);
	}

	@RequestMapping(value = "/branches/{path}/actions/unlock", method = RequestMethod.POST)
	@ResponseBody
	public void unlockBranch(@PathVariable String path) {
		branchService.unlock(BranchPathUriUtil.decodePath(path));
	}

	@ResponseBody
	@RequestMapping(value = "/reviews", method = RequestMethod.POST)
	public ResponseEntity<Void> createBranchReview(@RequestBody @Valid CreateReviewRequest createReviewRequest) {
		BranchReview branchReview = reviewService.getCreateReview(createReviewRequest.getSource(), createReviewRequest.getTarget());
		final String id = branchReview.getId();
		return ControllerHelper.getCreatedResponse(id);
	}

	@ResponseBody
	@RequestMapping(value = "/reviews/{id}", method = RequestMethod.GET)
	public BranchReview getBranchReview(@PathVariable String id) {
		return ControllerHelper.throwIfNotFound("Branch review", reviewService.getBranchReview(id));
	}

	@ResponseBody
	@RequestMapping(value = "/reviews/{id}/concept-changes", method = RequestMethod.GET)
	public BranchReviewConceptChanges getBranchReviewConceptChanges(@PathVariable String id) {
		BranchReview branchReview = reviewService.getBranchReviewOrThrow(id);
		if (branchReview.getStatus() != ReviewStatus.CURRENT) {
			throw new IllegalStateException("Branch review status must be " + ReviewStatus.CURRENT + " but is " + branchReview.getStatus());
		}
		return new BranchReviewConceptChanges(branchReview.getChangedConcepts());
	}

	@ResponseBody
	@RequestMapping(value = "/merge-reviews", method = RequestMethod.POST)
	public ResponseEntity<Void> createMergeReview(@RequestBody @Valid CreateReviewRequest createReviewRequest) {
		MergeReview mergeReview = reviewService.createMergeReview(createReviewRequest.getSource(), createReviewRequest.getTarget());
		return ControllerHelper.getCreatedResponse(mergeReview.getId());
	}

	@ResponseBody
	@RequestMapping(value = "/merge-reviews/{id}", method = RequestMethod.GET)
	public MergeReview getMergeReview(@PathVariable String id) {
		return ControllerHelper.throwIfNotFound("Merge review", reviewService.getMergeReview(id));
	}

	@ResponseBody
	@RequestMapping(value = "/merge-reviews/{id}/details", method = RequestMethod.GET)
	public Collection<MergeReviewConceptVersions> getMergeReviewConflictingConcepts(
			@PathVariable String id,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return reviewService.getMergeReviewConflictingConcepts(id, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@RequestMapping(value = "/merge-reviews/{id}/{conceptId}", method = RequestMethod.POST)
	public void saveMergeReviewConflictingConcept(@PathVariable String id, @PathVariable Long conceptId, @RequestBody Concept manuallyMergedConcept) throws ServiceException {
		reviewService.persistManuallyMergedConcept(id, conceptId, manuallyMergedConcept);
	}

	@RequestMapping(value = "/merge-reviews/{id}/{conceptId}", method = RequestMethod.DELETE)
	public void deleteMergeReviewConflictingConcept(@PathVariable String id, @PathVariable Long conceptId) {
		reviewService.persistManualMergeConceptDeletion(id, conceptId);
	}

	@RequestMapping(value = "/merge-reviews/{id}/apply", method = RequestMethod.POST)
	public void applyMergeReview(@PathVariable String id) throws ServiceException {
		reviewService.applyMergeReview(id);
	}

	@RequestMapping(value = "/merges", method = RequestMethod.POST)
	@ApiOperation(value = "Perform a branch rebase or promotion.",
			notes = "The integrity-check endpoint should be used before performing a promotion to avoid promotion errors.")
	public ResponseEntity<Void> mergeBranch(@RequestBody MergeRequest mergeRequest) {
		BranchMergeJob mergeJob = branchMergeService.mergeBranchAsync(mergeRequest);
		return ControllerHelper.getCreatedResponse(mergeJob.getId());
	}

	@RequestMapping(value = "/merges/{mergeId}", method = RequestMethod.GET)
	public BranchMergeJob retrieveMerge(@PathVariable String mergeId) {
		return branchMergeService.getBranchMergeJobOrThrow(mergeId);
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/integrity-check", method = RequestMethod.POST)
	@ApiOperation(value = "Perform integrity check against changed components on this branch.",
			notes = "Returns a report containing an entry for each type of issue found together with a map of components. " +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	public IntegrityIssueReport integrityCheck(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull final String branchPath) throws ServiceException {
		Branch branch = branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branchPath));
		return integrityService.findChangedComponentsWithBadIntegrity(branch);
	}


	@ResponseBody
	@RequestMapping(value = "/{branch}/upgrade-integrity-check", method = RequestMethod.POST)
	@ApiOperation(value = "Perform integrity check against changed components during extension upgrade on the fix branch.",
			notes = "Returns a report containing an entry for each type of issue found together with a map of components which still need to be fixed." +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	public IntegrityIssueReport upgradeIntegrityCheck(
			@ApiParam(value="The fix branch path") @PathVariable(value="branch") @NotNull final String branchPath,
			@ApiParam(value="Extension main branch e.g MAIN/{Code System}") @RequestParam @NotNull String extensionMainBranchPath) throws ServiceException {
		Branch branch = branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branchPath));
		if ("MAIN".equalsIgnoreCase(extensionMainBranchPath)) {
			throw new IllegalArgumentException("Extension main branch path can't be MAIN");
		}
		Branch extensionMainBranch = branchService.findBranchOrThrow(extensionMainBranchPath);
		if (!"MAIN".equalsIgnoreCase(PathUtil.getParentPath(extensionMainBranch.getPath()))) {
			throw new IllegalArgumentException("The parent of an extension main branch must be MAIN but is " + PathUtil.getParentPath(extensionMainBranch.getPath()));
		}
		// check task is a descendant of extension main
		if (!isDescendant(branch, extensionMainBranch.getPath())) {
			throw new IllegalArgumentException(String.format("Branch %s is not a descendant of %s", branch.getPath(), extensionMainBranchPath));
		}
		return integrityService.findChangedComponentsWithBadIntegrity(branch, extensionMainBranchPath);
	}

	private boolean isDescendant(Branch branch, String extensionMainBranchPath) {
		List<Branch> childrenBranches = clearMetadata(branchMergeService.findChildBranches(extensionMainBranchPath, false, PageRequest.of(0, 200)));
		List<String> branchPaths = childrenBranches.stream().map(Branch::getPath).collect(Collectors.toList());
		return branchPaths.contains(branch.getPath());
	}

	@ResponseBody
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
