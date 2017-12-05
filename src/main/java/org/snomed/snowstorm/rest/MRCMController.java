package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
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
	public org.snomed.snowstorm.rest.pojo.ItemsPage<ConceptMini> retrieveDomainAttributes(@PathVariable String path, @RequestParam Set<Long> parentIds) {
		String branchPath = BranchPathUriUtil.parseBranchPath(path);
		return getItemsPageWithNestedFSNs(mrcmService.retrieveDomainAttributes(branchPath, parentIds));
	}

	@ApiOperation("Retrieve valid values for the given attribute and term prefix.")
	@RequestMapping(value = "/mrcm/{path}/attribute-values/{attributeId}", method = RequestMethod.GET)
	@ResponseBody
	public org.snomed.snowstorm.rest.pojo.ItemsPage<ConceptMini> retrieveAttributeValues(@PathVariable String path, @PathVariable String attributeId, @RequestParam String termPrefix) {
		String branchPath = BranchPathUriUtil.parseBranchPath(path);
		return getItemsPageWithNestedFSNs(mrcmService.retrieveAttributeValues(branchPath, attributeId, termPrefix));
	}

	private org.snomed.snowstorm.rest.pojo.ItemsPage<ConceptMini> getItemsPageWithNestedFSNs(Collection<ConceptMini> conceptMinis) {
		conceptMinis.forEach(ConceptMini::nestFsn);
		return new ItemsPage<>(conceptMinis);
	}

}
