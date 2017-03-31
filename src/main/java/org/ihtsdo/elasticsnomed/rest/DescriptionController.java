package org.ihtsdo.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.ihtsdo.elasticsnomed.domain.Description;
import org.ihtsdo.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class DescriptionController {

	@Autowired
	private ConceptService conceptService;

	@RequestMapping(value = "/{branch}/descriptions", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<Description> findConcepts(@PathVariable String branch, @RequestParam(required = false) String term,
										  @RequestParam(defaultValue = "0") int number, @RequestParam(defaultValue = "100") int size) {
		return new Page<>(conceptService.findDescriptions(BranchPathUriUtil.parseBranchPath(branch), term, new PageRequest(number, size)));
	}

}
