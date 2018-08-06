package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.snomed.snowstorm.core.data.domain.BranchMergeJob;
import org.snomed.snowstorm.core.data.services.BranchMergeService;
import org.snomed.snowstorm.core.data.services.IntegrityService;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.rest.pojo.CreateBranchRequest;
import org.snomed.snowstorm.rest.pojo.MergeRequest;
import org.snomed.snowstorm.rest.pojo.UpdateBranchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.util.List;

@RestController
@RequestMapping(produces = "application/json")
public class BranchController {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private IntegrityService integrityService;

	@ApiOperation("Retrieve all branches")
	@RequestMapping(value = "/branches", method = RequestMethod.GET)
	@ResponseBody
	public List<Branch> retrieveAllBranches() {
		return branchService.findAll();
	}

	@RequestMapping(value = "/branches", method = RequestMethod.POST)
	@ResponseBody
	public Branch createBranch(@RequestBody CreateBranchRequest request) {
		return branchService.create(request.getParent() + "/" + request.getName(), request.getMetadata());
	}

	@ApiOperation("Update branch metadata")
	@RequestMapping(value = "/branches/{path}", method = RequestMethod.PUT)
	@ResponseBody
	public Branch updateBranch(@PathVariable String path, @RequestBody UpdateBranchRequest request) {
		return branchService.updateMetadata(path, request.getMetadata());
	}

	@ApiOperation("Retrieve a single branch")
	@RequestMapping(value = "/branches/{path}", method = RequestMethod.GET)
	@ResponseBody
	public Branch retrieveBranch(@PathVariable String path, @RequestParam(required = false, defaultValue = "false") boolean includeInheritedMetadata) {
		return branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(path), includeInheritedMetadata);
	}

	@RequestMapping(value = "/branches/{path}/actions/unlock", method = RequestMethod.POST)
	@ResponseBody
	public void unlockBranch(@PathVariable String path) {
		branchService.unlock(BranchPathUriUtil.decodePath(path));
	}

	@RequestMapping(value = "/merges", method = RequestMethod.POST)
	@ApiOperation(value = "Perform a branch rebase or promotion.",
			notes = "The integrity-check endpoint should be used before performing a promotion to avoid a promotion errors.")
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
	public IntegrityIssueReport integrityCheck(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull final String branchPath) {
		Branch branch = branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branchPath));
		return integrityService.findChangedComponentsWithBadIntegrity(branch);
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/integrity-check-full", method = RequestMethod.POST)
	@ApiOperation(value = "Perform integrity check against all components on this branch.",
			notes = "Returns a report containing an entry for each type of issue found together with a map of components. " +
					"In the component map each key represents an existing component and the corresponding map value is the id of a component which is missing or inactive.")
	public IntegrityIssueReport fullIntegrityCheck(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull final String branchPath) {
		Branch branch = branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(branchPath));
		return integrityService.findAllComponentsWithBadIntegrity(branch, true);
	}

}
