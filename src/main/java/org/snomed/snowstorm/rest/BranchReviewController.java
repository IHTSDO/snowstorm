package org.snomed.snowstorm.rest;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.review.BranchReview;
import org.snomed.snowstorm.core.data.domain.review.BranchReviewConceptChanges;
import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReviewConceptVersions;
import org.snomed.snowstorm.core.data.services.BranchReviewService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.rest.pojo.CreateReviewRequest;
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
		if (!conceptId.equals(manuallyMergedConcept.getConceptId())) {
			throw new IllegalArgumentException("conceptId in request path does not match the conceptId in the request body.");
		}
		mergeReview.putManuallyMergedConcept(manuallyMergedConcept);
	}

	@RequestMapping(value = "/merge-reviews/{id}/apply", method = RequestMethod.POST)
	public void applyMergeReview(@PathVariable String id) throws ServiceException {
		reviewService.applyMergeReview(id);
	}

}
