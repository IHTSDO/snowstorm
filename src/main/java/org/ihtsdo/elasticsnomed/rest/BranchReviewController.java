package org.ihtsdo.elasticsnomed.rest;

import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.review.BranchReview;
import org.ihtsdo.elasticsnomed.core.data.domain.review.BranchReviewConceptChanges;
import org.ihtsdo.elasticsnomed.core.data.domain.review.MergeReview;
import org.ihtsdo.elasticsnomed.core.data.domain.review.MergeReviewConceptVersions;
import org.ihtsdo.elasticsnomed.core.data.services.BranchReviewService;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
import org.ihtsdo.elasticsnomed.rest.pojo.CreateReviewRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import java.util.Collection;

@RestController
@RequestMapping(produces = "application/json")
public class BranchReviewController {

	@Autowired
	private BranchReviewService reviewService;

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
		return reviewService.getBranchReview(id);
	}

	@ResponseBody
	@RequestMapping(value = "/reviews/{id}/concept-changes", method = RequestMethod.GET)
	public BranchReviewConceptChanges getBranchReviewConceptChanges(@PathVariable String id) {
		return reviewService.getBranchReviewConceptChanges(id);
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
		return reviewService.getMergeReview(id);
	}

	@ResponseBody
	@RequestMapping(value = "/merge-reviews/{id}/details", method = RequestMethod.GET)
	public Collection<MergeReviewConceptVersions> getMergeReviewConflictingConcepts(@PathVariable String id) {
		return reviewService.getMergeReviewConflictingConcepts(id);
	}

	@RequestMapping(value = "/merge-reviews/{id}/{conceptId}", method = RequestMethod.POST)
	public void getMergeReviewConflictingConcepts(@PathVariable String id, @PathVariable String conceptId, @RequestBody Concept manuallyMergedConcept) {
		final MergeReview mergeReview = reviewService.getMergeReviewOrThrow(id);
		if (conceptId.equals(manuallyMergedConcept.getConceptId())) {
			throw new IllegalArgumentException("coneptId in request path does not match the conceptId in the request body.");
		}
		mergeReview.putManuallyMergedConcept(manuallyMergedConcept);
	}

	@RequestMapping(value = "/merge-reviews/{id}/apply", method = RequestMethod.POST)
	public void applyMergeReview(@RequestParam String id) throws ServiceException {
		reviewService.applyMergeReview(id);
	}

}
