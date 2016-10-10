package com.kaicube.snomed.elasticsnomed.rest;

import com.kaicube.elasticversioncontrol.api.BranchService;
import com.kaicube.elasticversioncontrol.domain.Branch;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BranchController {

	@Autowired
	private BranchService branchService;

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
		branchService.forceUnlock(ControllerHelper.parseBranchPath(path));
	}

}
