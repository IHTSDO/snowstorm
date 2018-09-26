package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.rest.converter.AggregationNameConverter;
import org.snomed.snowstorm.rest.pojo.BrowserDescriptionSearchResult;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.snomed.snowstorm.rest.pojo.PageWithFilters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@Api(tags = "Descriptions", description = "-")
@RequestMapping(produces = "application/json")
public class DescriptionController {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;

	private final AggregationNameConverter languageAggregationNameConverter = new AggregationNameConverter() {
		@Override
		public boolean canConvert(String aggregationGroupName) {
			return aggregationGroupName.equals("language");
		}

		@Override
		public String convert(String aggregationName) {
			return new Locale(aggregationName).getDisplayLanguage().toLowerCase();
		}
	};

	@RequestMapping(value = "browser/{branch}/descriptions", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<BrowserDescriptionSearchResult> findBrowserDescriptions(@PathVariable String branch, @RequestParam(required = false) String term,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {

		branch = BranchPathUriUtil.decodePath(branch);
		PageRequest pageRequest = ControllerHelper.getPageRequest(offset, limit);

		AggregatedPage<Description> page = descriptionService.findDescriptionsWithAggregations(branch, term, pageRequest);
		Set<String> conceptIds = page.getContent().stream().map(Description::getConceptId).collect(Collectors.toSet());
		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branch, conceptIds).getResultsMap();

		List<BrowserDescriptionSearchResult> results = new ArrayList<>();
		page.getContent().forEach(d -> results.add(new BrowserDescriptionSearchResult(d.getTerm(), d.isActive(), conceptMinis.get(d.getConceptId()))));

		return new PageWithFilters<>(results, pageRequest, page.getTotalElements(), page.getAggregations(), languageAggregationNameConverter);
	}

	@RequestMapping(value = "{branch}/descriptions", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<Description> findDescriptions(@PathVariable String branch,
			@RequestParam(required = false) @ApiParam("The concept id to match") String concept,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit,
			@RequestHeader("Accept-Language") String acceptLanguage) {

		branch = BranchPathUriUtil.decodePath(branch);
		return new ItemsPage<>(descriptionService.findDescriptions(branch, null, concept, ControllerHelper.getPageRequest(offset, limit)));
	}

	@RequestMapping(value = "{branch}/descriptions/{descriptionId}", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Description fetchDescription(@PathVariable String branch, @PathVariable String descriptionId) {
		return ControllerHelper.throwIfNotFound("Description", descriptionService.findDescription(BranchPathUriUtil.decodePath(branch), descriptionId));
	}

}
