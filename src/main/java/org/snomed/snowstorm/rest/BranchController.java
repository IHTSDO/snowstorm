package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
		List<Branch> allBranches = branchService.findAll();
		// Clear metadata
		for (Branch branch : allBranches) {
			branch.setMetadata(null);
		}
		return allBranches;
	}
	
	@ApiOperation("Retrieve branch descendants")
	@RequestMapping(value = "/branches/{path}/children", method = RequestMethod.GET)
	@ResponseBody
	public List<Branch> retrieveBranchDescendants(
			@PathVariable String path,
			@RequestParam(required = false, defaultValue = "false") boolean immediateChildren) {
		List<Branch> descendants = branchService.findChildren(BranchPathUriUtil.decodePath(path), immediateChildren);
		// Clear metadata
		for (Branch branch : descendants) {
			branch.setMetadata(null);
		}
		return descendants;
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
		if (branchService.findBranchOrThrow(path).isLocked()) {
			throw new IllegalStateException("Branch metadata can not be updated when branch is locked.");
		}
		Map<String, String> metadata = branchMetadataHelper.flattenObjectValues(request.getMetadata());
		return getBranchPojo(branchService.updateMetadata(BranchPathUriUtil.decodePath(path), metadata));
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
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return reviewService.getMergeReviewConflictingConcepts(id, ControllerHelper.getLanguageCodes(acceptLanguageHeader));
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
	@RequestMapping(value = "/{branch}/integrity-check-full", method = RequestMethod.POST)
	@ApiOperation(value = "Perform integrity check against all components on this branch.",
			notes = "Returns a report containing an entry for each type of issue found together with a map of components. " +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	public IntegrityIssueReport fullIntegrityCheck(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull final String branchPath) throws ServiceException {
		Branch branch = branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branchPath));
		return integrityService.findAllComponentsWithBadIntegrity(branch, true);
	}

}
