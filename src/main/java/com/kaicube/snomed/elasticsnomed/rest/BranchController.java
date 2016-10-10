package com.kaicube.snomed.elasticsnomed.rest;

import com.kaicube.elasticversioncontrol.api.BranchService;
import com.kaicube.elasticversioncontrol.domain.Branch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class BranchController {

	@Autowired
	private BranchService branchService;

	@ResponseBody
	@RequestMapping(value = "/branches", method = RequestMethod.POST, produces = "application/json")
	public Branch createBranch(@RequestParam String branchPath) {
		return branchService.create(ControllerHelper.parseBranchPath(branchPath));
	}

	@ResponseBody
	@RequestMapping(value = "/branches/{branch}/actions/unlock", method = RequestMethod.POST, produces = "application/json")
	public void unlockBranch(@PathVariable String branch) {
		branchService.forceUnlock(ControllerHelper.parseBranchPath(branch));
	}

}
