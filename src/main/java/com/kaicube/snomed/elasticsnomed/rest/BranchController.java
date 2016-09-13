package com.kaicube.snomed.elasticsnomed.rest;

import com.kaicube.elasticversioncontrol.api.BranchService;
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
	@RequestMapping(value = "/{branch}/actions/unlock", method = RequestMethod.POST, produces = "application/json")
	public void unlockBranch(@PathVariable String branch) {
		branchService.forceUnlock(branch);
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concept-changes", method = RequestMethod.GET, produces = "application/json")
	public Collection<String> listChangedConceptIds(@PathVariable String branch) {
		return conceptService.listChangedConceptIds(branch);
	}

}
