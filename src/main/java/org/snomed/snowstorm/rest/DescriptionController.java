package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.TooCostlyException;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.LangUtil;
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

	@ApiOperation(value = "Search for concept descriptions.",
			notes = "The Accept-Language header is used to specify the user's preferred language, 'en' is always added as a fallback if not already included in the list. " +
					"Each language is used as an optional clause for matching and will include the correct character folding behaviour for that language. " +
					"The Accept-Language header list is also used to chose the best translated FSN and PT values in the response.")
	@RequestMapping(value = "browser/{branch}/descriptions", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<BrowserDescriptionSearchResult> findBrowserDescriptions(
			@PathVariable String branch,
			@RequestParam(required = false) String term,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String module,

			@ApiParam(value = "Set of two character language codes to match. " +
					"The English language code 'en' will not be added automatically, in contrast to the Accept-Language header which always includes it. " +
					"Accept-Language header still controls result FSN and PT language selection.")
			@RequestParam(required = false) Set<String> language,

			@ApiParam(value = "Set of description types to include. Pick descendants of '900000000000446008 | Description type (core metadata concept) |'.")
			@RequestParam(required = false) Set<Long> type,

			@Deprecated
			@RequestParam(required = false) String semanticTag,

			@ApiParam(value = "Set of semantic tags.")
			@RequestParam(required = false) Set<String> semanticTags,

			@ApiParam(value = "Set of description language reference sets. The description must be preferred in at least one of these to match.")
			@RequestParam(required = false) Set<Long> preferredIn,

			@ApiParam(value = "Set of description language reference sets. The description must be acceptable in at least one of these to match.")
			@RequestParam(required = false) Set<Long> acceptableIn,

			@ApiParam(value = "Set of description language reference sets. The description must be preferred OR acceptable in at least one of these to match.")
			@RequestParam(required = false) Set<Long> preferredOrAcceptableIn,

			@RequestParam(required = false) Boolean conceptActive,
			@RequestParam(required = false) String conceptRefset,
			@RequestParam(defaultValue = "false") boolean groupByConcept,
			@RequestParam(defaultValue = "STANDARD") DescriptionService.SearchMode searchMode,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) throws TooCostlyException {

		branch = BranchPathUriUtil.decodePath(branch);
		PageRequest pageRequest = ControllerHelper.getPageRequest(offset, limit);

		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader);

		PageWithBucketAggregations<Description> page = descriptionService.findDescriptionsWithAggregations(
				branch, new DescriptionCriteria()
						// Description clauses
						.term(term)
						.active(active)
						.module(module)
						.searchLanguageCodes(language)
						.type(type)
						.semanticTag(semanticTag)
						.semanticTags(semanticTags)
						// Language reference set clauses
						.preferredIn(preferredIn)
						.acceptableIn(acceptableIn)
						.preferredOrAcceptableIn(preferredOrAcceptableIn)

						// Concept clauses
						.conceptActive(conceptActive)
						.conceptRefset(conceptRefset)
						.groupByConcept(groupByConcept)
						.searchMode(searchMode),
				// Page
				pageRequest);

		Set<String> conceptIds = page.getContent().stream().map(Description::getConceptId).collect(Collectors.toSet());
		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branch, conceptIds, languageDialects).getResultsMap();

		List<BrowserDescriptionSearchResult> results = new ArrayList<>();
		page.getContent().forEach(d -> results.add(new BrowserDescriptionSearchResult(d.getTerm(), d.isActive(), d.getLanguageCode(), d.getModuleId(), conceptMinis.get(d.getConceptId()))));

		PageWithBucketAggregations<BrowserDescriptionSearchResult> pageWithBucketAggregations = new PageWithBucketAggregations<>(results, page.getPageable(), page.getTotalElements(), page.getBuckets());
		addBucketConcepts(branch, languageDialects, pageWithBucketAggregations);
		addLanguageNames(pageWithBucketAggregations);
		return pageWithBucketAggregations;
	}

	private void addBucketConcepts(@PathVariable String branch, List<LanguageDialect> LanguageDialect, PageWithBucketAggregations<BrowserDescriptionSearchResult> pageWithBucketAggregations) {
		Map<String, Map<String, Long>> buckets = pageWithBucketAggregations.getBuckets();
		Set<String> bucketConceptIds = new HashSet<>();
		if (buckets.containsKey("membership")) {
			bucketConceptIds.addAll(buckets.get("membership").keySet());
		}
		if (buckets.containsKey("module")) {
			bucketConceptIds.addAll(buckets.get("module").keySet());
		}
		if (!bucketConceptIds.isEmpty()) {
			pageWithBucketAggregations.setBucketConcepts(conceptService.findConceptMinis(branch, bucketConceptIds, LanguageDialect).getResultsMap());
		}
	}

	private void addLanguageNames(PageWithBucketAggregations<BrowserDescriptionSearchResult> aggregations) {
		Map<String, Long> language = aggregations.getBuckets().get("language");
		if (language != null) {
			Map<String, String> languageCodeToName = new HashMap<>();
			for (String languageCode : language.keySet()) {
				languageCodeToName.put(languageCode, LangUtil.convertLanguageCodeToName(languageCode).toLowerCase());
			}
			aggregations.setLanguageNames(languageCodeToName);
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

	@ApiOperation(value = "Delete a description.")
	@RequestMapping(value = "{branch}/descriptions/{descriptionId}", method = RequestMethod.DELETE)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public void deleteDescription(
			@PathVariable String branch,
			@PathVariable String descriptionId,
			@ApiParam("Force the deletion of a released description.")
			@RequestParam(defaultValue = "false") boolean force) {
		branch = BranchPathUriUtil.decodePath(branch);
		Description description = ControllerHelper.throwIfNotFound("Description", descriptionService.findDescription(branch, descriptionId));
		descriptionService.deleteDescription(description, branch, force);
	}

	@ApiOperation("List semantic tags of all active concepts together with a count of concepts using each.")
	@RequestMapping(value = "{branch}/descriptions/semantictags", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Map<String, Long> countSemanticTags(@PathVariable String branch) {

		branch = BranchPathUriUtil.decodePath(branch);
		return descriptionService.countActiveConceptsPerSemanticTag(branch);
	}

}
