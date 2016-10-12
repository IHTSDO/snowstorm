package com.kaicube.snomed.elasticsnomed.rest;

import com.kaicube.snomed.elasticsnomed.domain.review.BranchReview;
import com.kaicube.snomed.elasticsnomed.domain.review.BranchReviewConceptChanges;
import com.kaicube.snomed.elasticsnomed.rest.pojo.CreateReviewRequest;
import com.kaicube.snomed.elasticsnomed.services.BranchReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
public class BranchReviewController {

	@Autowired
	private BranchReviewService reviewService;

	@ResponseBody
	@RequestMapping(value = "/reviews", method = RequestMethod.POST, produces = "application/json")
	public ResponseEntity<Object> createBranchReview(@RequestBody @Valid CreateReviewRequest createReviewRequest) {
		BranchReview branchReview = reviewService.createReview(createReviewRequest.getSource(), createReviewRequest.getTarget());
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

}
