package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
	public ItemsPage<ConceptMini> retrieveDomainAttributes(@PathVariable String path, @RequestParam Set<Long> parentIds) {
		String branchPath = BranchPathUriUtil.decodePath(path);
		return getItemsPageWithNestedFSNs(mrcmService.retrieveDomainAttributes(branchPath, parentIds));
	}

	@ApiOperation("Retrieve valid values for the given attribute and term prefix.")
	@RequestMapping(value = "/mrcm/{path}/attribute-values/{attributeId}", method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<ConceptMini> retrieveAttributeValues(@PathVariable String path, @PathVariable String attributeId, @RequestParam String termPrefix) {
		String branchPath = BranchPathUriUtil.decodePath(path);
		return getItemsPageWithNestedFSNs(mrcmService.retrieveAttributeValues(branchPath, attributeId, termPrefix));
	}

	@ApiOperation("Update system wide MRCM from XML file. This is an alternative implementation to the reference sets.")
	@RequestMapping(value = "/mrcm", method = RequestMethod.POST, consumes = "multipart/form-data")
	public void updateMRCMFromXml(@RequestParam MultipartFile file) throws IOException {
		mrcmService.loadFromStream(file.getInputStream());
	}

	private ItemsPage<ConceptMini> getItemsPageWithNestedFSNs(Collection<ConceptMini> conceptMinis) {
		conceptMinis.forEach(ConceptMini::nestFsn);
		return new ItemsPage<>(conceptMinis);
	}

}
