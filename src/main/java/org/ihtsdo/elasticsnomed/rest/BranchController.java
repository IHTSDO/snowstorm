package org.ihtsdo.elasticsnomed.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.elasticsnomed.domain.BranchMergeJob;
import org.ihtsdo.elasticsnomed.rest.pojo.MergeRequest;
import org.ihtsdo.elasticsnomed.services.BranchMergeService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BranchController {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@ApiOperation("Retrieve all branches")
	@RequestMapping(value = "/branches", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public List<Branch> retrieveAllBranches() {
		return branchService.findAll();
	}

	@RequestMapping(value = "/branches", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public Branch createBranch(@RequestParam String branchPath) {
		return branchService.create(ControllerHelper.parseBranchPath(branchPath));
	}

	@ApiOperation("Retrieve a single branch")
	@RequestMapping(value = "/branches/{path}", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public Branch retrieveBranch(@PathVariable String path) {
		return branchService.findBranchOrThrow(ControllerHelper.parseBranchPath(path));
	}

	@RequestMapping(value = "/branches/{path}/actions/unlock", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public void unlockBranch(@PathVariable String path) {
		branchService.unlock(ControllerHelper.parseBranchPath(path));
	}

	@RequestMapping(value = "/merges", method = RequestMethod.POST, produces = "application/json")
	public ResponseEntity<Object> mergeBranch(@RequestBody MergeRequest mergeRequest) {
		BranchMergeJob mergeJob = branchMergeService.mergeBranchAsync(mergeRequest);
		return ControllerHelper.getCreatedResponse(mergeJob.getId());
	}

}
