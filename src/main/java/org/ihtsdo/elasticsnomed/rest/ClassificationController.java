package org.ihtsdo.elasticsnomed.rest;

import io.swagger.annotations.ApiOperation;
import org.ihtsdo.elasticsnomed.core.data.domain.Classification;
import org.ihtsdo.elasticsnomed.core.data.services.ClassificationService;
import org.ihtsdo.elasticsnomed.rest.pojo.ClassificationUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/{path}/classifications", produces = "application/json")
public class ClassificationController {

	@Autowired
	private ClassificationService classificationService;

	@ApiOperation("Retrieve classifications on a branch")
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<Classification> findClassifications(@PathVariable String path) {
		return new ItemsPage<>(classificationService.findClassifications(path));
	}

	@ApiOperation("Retrieve a classifications on a branch")
	@RequestMapping(value = "/{classificationId}", method = RequestMethod.GET)
	@ResponseBody
	public Classification findClassification(@PathVariable String path, @PathVariable String classificationId) {
		return classificationService.findClassification(path, classificationId);
	}

	@ApiOperation("Create a classification on a branch")
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public Classification createClassification(@PathVariable String path, @RequestParam(required = false) String reasonerId) {
		return classificationService.createClassification(path, reasonerId);
	}

	@ApiOperation("Update a classification on a branch. Save classification results by updating status to 'SAVED'.")
	@RequestMapping(method = RequestMethod.PUT)
	public void updateClassification(@PathVariable String path, @PathVariable String classificationId, @RequestBody ClassificationUpdateRequest updateRequest) {
		if (updateRequest.getStatus() != Classification.Status.SAVED) {
			throw new IllegalArgumentException("The only expected status is " + Classification.Status.SAVED.toString());
		}
		classificationService.saveClassificationResults(path, classificationId);
	}

}
