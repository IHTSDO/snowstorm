package com.kaicube.snomed.elasticsnomed.rest;

import com.kaicube.elasticversioncontrol.api.BranchService;
import com.kaicube.elasticversioncontrol.api.PathUtil;
import com.kaicube.elasticversioncontrol.domain.Branch;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
public class BranchController {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

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

	@ResponseBody
	@RequestMapping(value = "/branches/{branch}/concept-changes", method = RequestMethod.GET, produces = "application/json")
	public Collection<String> listChangedConceptIds(@PathVariable String branch) {
		return conceptService.listChangedConceptIds(ControllerHelper.parseBranchPath(branch));
	}

}
