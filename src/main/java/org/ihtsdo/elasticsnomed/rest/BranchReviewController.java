package org.ihtsdo.elasticsnomed.rest;

import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.review.BranchReview;
import org.ihtsdo.elasticsnomed.core.data.domain.review.BranchReviewConceptChanges;
import org.ihtsdo.elasticsnomed.core.data.domain.review.MergeReview;
import org.ihtsdo.elasticsnomed.core.data.domain.review.MergeReviewConceptVersions;
import org.ihtsdo.elasticsnomed.rest.pojo.CreateReviewRequest;
import org.ihtsdo.elasticsnomed.core.data.services.BranchReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import javax.validation.Valid;

@RestController
public class BranchReviewController {

	@Autowired
	private BranchReviewService reviewService;

	@ResponseBody
	@RequestMapping(value = "/reviews", method = RequestMethod.POST, produces = "application/json")
	public ResponseEntity<Object> createBranchReview(@RequestBody @Valid CreateReviewRequest createReviewRequest) {
		BranchReview branchReview = reviewService.getCreateReview(createReviewRequest.getSource(), createReviewRequest.getTarget());
		final String id = branchReview.getId();
		return ControllerHelper.getCreatedResponse(id);
	}

	@ResponseBody
	@RequestMapping(value = "/reviews/{id}", method = RequestMethod.GET, produces = "application/json")
	public BranchReview getBranchReview(@PathVariable String id) {
		return reviewService.getBranchReview(id);
	}

	@ResponseBody
	@RequestMapping(value = "/reviews/{id}/concept-changes", method = RequestMethod.GET, produces = "application/json")
	public BranchReviewConceptChanges getBranchReviewConceptChanges(@PathVariable String id) {
		return reviewService.getBranchReviewConceptChanges(id);
	}

	@ResponseBody
	@RequestMapping(value = "/merge-reviews", method = RequestMethod.POST, produces = "application/json")
	public ResponseEntity<Object> createMergeReview(@RequestBody @Valid CreateReviewRequest createReviewRequest) {
		MergeReview mergeReview = reviewService.createMergeReview(createReviewRequest.getSource(), createReviewRequest.getTarget());
		return ControllerHelper.getCreatedResponse(mergeReview.getId());
	}

	@ResponseBody
	@RequestMapping(value = "/merge-reviews/{id}", method = RequestMethod.GET, produces = "application/json")
	public MergeReview getMergeReview(@PathVariable String id) {
		return reviewService.getMergeReview(id);
	}

	@ResponseBody
	@RequestMapping(value = "/merge-reviews/{id}/details", method = RequestMethod.GET, produces = "application/json")
	public Collection<MergeReviewConceptVersions> getMergeReviewConflictingConcepts(@PathVariable String id) {
		return reviewService.getMergeReviewConflictingConcepts(id);
	}

	@RequestMapping(value = "/merge-reviews/{id}/{conceptId}", method = RequestMethod.POST, produces = "application/json")
	public void getMergeReviewConflictingConcepts(@PathVariable String id, @PathVariable String conceptId, @RequestBody Concept manuallyMergedConcept) {
		final MergeReview mergeReview = reviewService.getMergeReviewOrThrow(id);
		mergeReview.putManuallyMergedConcept(manuallyMergedConcept);
	}

	@RequestMapping(value = "/merge-reviews/{id}/apply", method = RequestMethod.POST, produces = "application/json")
	public void applyMergeReview(@RequestParam String id) {
		reviewService.applyMergeReview(id);
	}

}
