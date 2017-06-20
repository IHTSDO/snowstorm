package org.ihtsdo.elasticsnomed.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.ApiOperation;
import org.ihtsdo.elasticsnomed.core.data.domain.ConceptMini;
import org.ihtsdo.elasticsnomed.mrcm.MRCMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Set;

@RestController
public class MRCMController {

	@Autowired
	private MRCMService mrcmService;

	@ApiOperation("Retrieve MRCM domain attributes applicable for the given parents.")
	@RequestMapping(value = "/mrcm/{path}/domain-attributes", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public Collection<ConceptMini> retrieveBranch(@PathVariable String path, @RequestParam Set<Long> parentIds) {
		String branchPath = BranchPathUriUtil.parseBranchPath(path);
		return mrcmService.retrieveDomainAttributes(branchPath, parentIds);
	}

}
