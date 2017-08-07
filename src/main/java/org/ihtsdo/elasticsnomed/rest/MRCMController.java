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
@RequestMapping(produces = "application/json")
public class MRCMController {

	@Autowired
	private MRCMService mrcmService;

	@ApiOperation("Retrieve MRCM domain attributes applicable for the given parents.")
	@RequestMapping(value = "/mrcm/{path}/domain-attributes", method = RequestMethod.GET)
	@ResponseBody
	public Collection<ConceptMini> retrieveDomainAttributes(@PathVariable String path, @RequestParam Set<Long> parentIds) {
		String branchPath = BranchPathUriUtil.parseBranchPath(path);
		return mrcmService.retrieveDomainAttributes(branchPath, parentIds);
	}

	@ApiOperation("Retrieve valid values for the given attribute and term prefix.")
	@RequestMapping(value = "/mrcm/{path}/attribute-values/{attributeId}", method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<ConceptMini> retrieveAttributeValues(@PathVariable String path, @PathVariable String attributeId, @RequestParam String termPrefix) {
		String branchPath = BranchPathUriUtil.parseBranchPath(path);
		Collection<ConceptMini> conceptMinis = mrcmService.retrieveAttributeValues(branchPath, attributeId, termPrefix);
		conceptMinis.forEach(ConceptMini::nestFsn);
		return new ItemsPage<>(conceptMinis);
	}

}
