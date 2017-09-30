package org.ihtsdo.elasticsnomed.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.ApiOperation;
import org.ihtsdo.elasticsnomed.core.data.domain.classification.Classification;
import org.ihtsdo.elasticsnomed.core.data.domain.classification.RelationshipChange;
import org.ihtsdo.elasticsnomed.core.data.services.classification.ClassificationService;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
import org.ihtsdo.elasticsnomed.rest.pojo.ClassificationUpdateRequest;
import org.ihtsdo.elasticsnomed.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping(value = "/{branch}/classifications", produces = "application/json")
public class ClassificationController {

	@Autowired
	private ClassificationService classificationService;

	@ApiOperation("Retrieve classifications on a branch")
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<Classification> findClassifications(@PathVariable String branch) {
		return new ItemsPage<>(classificationService.findClassifications(BranchPathUriUtil.parseBranchPath(branch)));
	}

	@ApiOperation("Retrieve a classification on a branch")
	@RequestMapping(value = "/{classificationId}", method = RequestMethod.GET)
	@ResponseBody
	public Classification findClassification(@PathVariable String branch, @PathVariable String classificationId) {
		return classificationService.findClassification(BranchPathUriUtil.parseBranchPath(branch), classificationId);
	}

	@ApiOperation("Retrieve relationship changes made by a classification run on a branch")
	@RequestMapping(value = "/{classificationId}/relationship-changes", method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<RelationshipChange> getRelationshipChanges(@PathVariable String branch, @PathVariable String classificationId,
																@RequestParam(required = false, defaultValue = "0") int page,
																@RequestParam(required = false, defaultValue = "1000") int pageSize) {
		return new ItemsPage<>(classificationService.getRelationshipChanges(BranchPathUriUtil.parseBranchPath(branch), classificationId, new PageRequest(page, pageSize)));
	}

	@ApiOperation("Create a classification on a branch")
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity createClassification(@PathVariable String branch,
													   @RequestParam(required = false) String reasonerId,
													   UriComponentsBuilder uriComponentsBuilder) throws ServiceException {
		branch = BranchPathUriUtil.parseBranchPath(branch);
		Classification classification = classificationService.createClassification(branch, reasonerId);
		return ResponseEntity.created(uriComponentsBuilder.path("/{branch}/classifications/{classificationId}")
				.buildAndExpand(branch, classification.getId()).toUri()).build();
	}

	@ApiOperation("Update a classification on a branch. Save classification results by updating status to 'SAVED'.")
	@RequestMapping(value = "/{classificationId}", method = RequestMethod.PUT)
	public void updateClassification(@PathVariable String branch, @PathVariable String classificationId, @RequestBody ClassificationUpdateRequest updateRequest) {
		if (updateRequest.getStatus() != Classification.Status.SAVED) {
			throw new IllegalArgumentException("The only expected status is " + Classification.Status.SAVED.toString());
		}
		classificationService.saveClassificationResultsToBranch(BranchPathUriUtil.parseBranchPath(branch), classificationId);
	}

}
