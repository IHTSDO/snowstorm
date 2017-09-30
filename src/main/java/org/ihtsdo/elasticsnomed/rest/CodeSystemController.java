package org.ihtsdo.elasticsnomed.rest;

import io.swagger.annotations.ApiOperation;
import org.ihtsdo.elasticsnomed.core.data.domain.CodeSystem;
import org.ihtsdo.elasticsnomed.core.data.domain.CodeSystemVersion;
import org.ihtsdo.elasticsnomed.core.data.services.CodeSystemService;
import org.ihtsdo.elasticsnomed.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/codesystems", produces = "application/json")
public class CodeSystemController {

	@Autowired
	private CodeSystemService codeSystemService;

	@ApiOperation("Retrieve all code systems")
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<CodeSystem> findAll() {
		return new ItemsPage<>(codeSystemService.findAll());
	}

	@ApiOperation("Retrieve a code system")
	@RequestMapping(value = "/{shortName}", method = RequestMethod.GET)
	@ResponseBody
	public CodeSystem findClassification(@PathVariable String shortName) {
		return ControllerHelper.throwIfNotFound("Code System", codeSystemService.find(shortName));
	}

	@ApiOperation("Retrieve all code system versions")
	@RequestMapping(value = "/{shortName}/versions", method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<CodeSystemVersion> findAllVersions(@PathVariable String shortName) {
		return new ItemsPage<>(codeSystemService.findAllVersions(shortName));
	}


//	@ApiOperation("Create a classification on a branch")
//	@RequestMapping(method = RequestMethod.POST)
//	@ResponseBody
//	public ResponseEntity createClassification(@PathVariable String branch,
//													   @RequestParam(required = false) String reasonerId,
//													   UriComponentsBuilder uriComponentsBuilder) throws ServiceException {
//		branch = BranchPathUriUtil.parseBranchPath(branch);
//		Classification classification = classificationService.createClassification(branch, reasonerId);
//		return ResponseEntity.created(uriComponentsBuilder.path("/{branch}/classifications/{classificationId}")
//				.buildAndExpand(branch, classification.getId()).toUri()).build();
//	}
//
//	@ApiOperation("Update a classification on a branch. Save classification results by updating status to 'SAVED'.")
//	@RequestMapping(method = RequestMethod.PUT)
//	public void updateClassification(@PathVariable String branch, @PathVariable String classificationId, @RequestBody ClassificationUpdateRequest updateRequest) {
//		if (updateRequest.getStatus() != Classification.Status.SAVED) {
//			throw new IllegalArgumentException("The only expected status is " + Classification.Status.SAVED.toString());
//		}
//		classificationService.saveClassificationResultsToBranch(BranchPathUriUtil.parseBranchPath(branch), classificationId);
//	}

}
