package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.rest.pojo.BrowserDescriptionSearchResult;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

	@RequestMapping(value = "browser/{branch}/descriptions", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<BrowserDescriptionSearchResult> findBrowserDescriptions(
			@PathVariable String branch,
			@RequestParam(required = false) String term,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String module,
			@RequestParam(required = false) String semanticTag,
			@RequestParam(required = false) Boolean conceptActive,
			@RequestParam(defaultValue = "false") boolean groupByConcept,
			@RequestParam(defaultValue = "STANDARD") DescriptionService.SearchMode searchMode,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		branch = BranchPathUriUtil.decodePath(branch);
		PageRequest pageRequest = ControllerHelper.getPageRequest(offset, limit);

		List<String> languageCodes = ControllerHelper.getLanguageCodes(acceptLanguageHeader);

		PageWithBucketAggregations<Description> page = descriptionService.findDescriptionsWithAggregations(
				branch,
				// Description clauses
				term, active, module, semanticTag,
				// Concept clauses
				conceptActive,
				// Grouping
				groupByConcept,
				//search mode
				searchMode,
				// Language and page
				languageCodes, pageRequest);

		Set<String> conceptIds = page.getContent().stream().map(Description::getConceptId).collect(Collectors.toSet());
		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branch, conceptIds, languageCodes).getResultsMap();

		List<BrowserDescriptionSearchResult> results = new ArrayList<>();
		page.getContent().forEach(d -> results.add(new BrowserDescriptionSearchResult(d.getTerm(), d.isActive(), d.getLanguageCode(), d.getModuleId(), conceptMinis.get(d.getConceptId()))));

		PageWithBucketAggregations<BrowserDescriptionSearchResult> pageWithBucketAggregations = new PageWithBucketAggregations<>(results, page.getPageable(), page.getTotalElements(), page.getBuckets());
		addBucketConcepts(branch, languageCodes, pageWithBucketAggregations);
		return pageWithBucketAggregations;
	}

	private void addBucketConcepts(@PathVariable String branch, List<String> languageCodes, PageWithBucketAggregations<BrowserDescriptionSearchResult> pageWithBucketAggregations) {
		Map<String, Map<String, Long>> buckets = pageWithBucketAggregations.getBuckets();
		Set<String> bucketConceptIds = new HashSet<>();
		if (buckets.containsKey("membership")) {
			bucketConceptIds.addAll(buckets.get("membership").keySet());
		}
		if (buckets.containsKey("module")) {
			bucketConceptIds.addAll(buckets.get("module").keySet());
		}
		if (!bucketConceptIds.isEmpty()) {
			pageWithBucketAggregations.setBucketConcepts(conceptService.findConceptMinis(branch, bucketConceptIds, languageCodes).getResultsMap());
		}
	}

	@RequestMapping(value = "{branch}/descriptions", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<Description> findDescriptions(@PathVariable String branch,
			@RequestParam(required = false) @ApiParam("The concept id to match") String concept,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {

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
