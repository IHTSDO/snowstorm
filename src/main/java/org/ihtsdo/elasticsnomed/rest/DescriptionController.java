package org.ihtsdo.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.ihtsdo.elasticsnomed.core.data.domain.ConceptMini;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.data.services.DescriptionService;
import org.ihtsdo.elasticsnomed.rest.pojo.DescriptionSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping(produces = "application/json")
public class DescriptionController {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;

	@RequestMapping(value = "browser/{branch}/descriptions", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public List<DescriptionSearchResult> findConcepts(@PathVariable String branch, @RequestParam(required = false) String query,
													  @RequestParam(defaultValue = "0") int pageNumber, @RequestParam(defaultValue = "50") int size) {
		branch = BranchPathUriUtil.parseBranchPath(branch);
		org.springframework.data.domain.Page<Description> page = descriptionService.findDescriptions(branch, query, new PageRequest(pageNumber, size));
		Set<String> conceptIds = page.getContent().stream().map(Description::getConceptId).collect(Collectors.toSet());
		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branch, conceptIds).getResultsMap();

		List<DescriptionSearchResult> results = new ArrayList<>();
		page.getContent().forEach(d -> results.add(new DescriptionSearchResult(d.getTerm(), d.isActive(), conceptMinis.get(d.getConceptId()))));

		// TODO - update snow-owl to use pagination
		// return new Page<>(new PageImpl<>(results, new PageRequest(number, size), page.getTotalElements()));

		return results;
	}

	@RequestMapping(value = "{branch}/descriptions/{descriptionId}", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Description fetchDescription(@PathVariable String branch, @PathVariable String descriptionId) {
		return ControllerHelper.throwIfNotFound("Description", descriptionService.fetchDescription(BranchPathUriUtil.parseBranchPath(branch), descriptionId));
	}

}
