package com.kaicube.snomed.elasticsnomed.rest;

import com.kaicube.elasticversioncontrol.api.BranchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class BranchController {

	@Autowired
	private BranchService branchService;

	@ResponseBody
	@RequestMapping(value = "/{branch}/actions/unlock", method = RequestMethod.POST, produces = "application/json")
	public void unlockBranch(@PathVariable String branch) {
		branchService.forceUnlock(branch);
	}


}
