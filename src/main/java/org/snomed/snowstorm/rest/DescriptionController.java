package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.unmodifiableSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@RestController
@Tag(name = "Descriptions", description = "-")
@RequestMapping(produces = "application/json")
public class DescriptionController {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;

	@Operation(summary = "Search for concept descriptions.",
			description = "The Accept-Language header is used to specify the user's preferred language, 'en' is always added as a fallback if not already included in the list. " +
					"Each language is used as an optional clause for matching and will include the correct character folding behaviour for that language. " +
					"The Accept-Language header list is also used to chose the best translated FSN and PT values in the response.")
	@GetMapping(value = "browser/{branch}/descriptions")
	@JsonView(value = View.Component.class)
	public Page<BrowserDescriptionSearchResult> findBrowserDescriptions(
			@PathVariable String branch,
			@RequestParam(required = false) String term,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Set<String> module,

			@Parameter(description = "Set of two character language codes to match. " +
					"The English language code 'en' will not be added automatically, in contrast to the Accept-Language header which always includes it. " +
					"Accept-Language header still controls result FSN and PT language selection.")
			@RequestParam(required = false) Set<String> language,

			@Parameter(description = "Set of description type ids to use include. Defaults to any. " +
					"Pick descendants of '900000000000446008 | Description type (core metadata concept) |'. " +
					"Examples: 900000000000003001 (FSN), 900000000000013009 (Synonym), 900000000000550004 (Definition)")
			@RequestParam(required = false) Set<Long> type,

			@Deprecated
			@RequestParam(required = false) String semanticTag,

			@Parameter(name = "Set of semantic tags.")
			@RequestParam(required = false) Set<String> semanticTags,

			@Parameter(name = "Set of description language reference sets. The description must be preferred in at least one of these to match.")
			@RequestParam(required = false) Set<Long> preferredIn,

			@Parameter(name = "Set of description language reference sets. The description must be acceptable in at least one of these to match.")
			@RequestParam(required = false) Set<Long> acceptableIn,

			@Parameter(name = "Set of description language reference sets. The description must be preferred OR acceptable in at least one of these to match.")
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
						.modules(module)
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

	@GetMapping(value = "{branch}/descriptions")
	@JsonView(value = View.Component.class)
	public ItemsPage<Description> findDescriptions(@PathVariable String branch,
			@RequestParam(required = false) @Parameter(description = "The concept id to match") String conceptId,
			@RequestParam(required = false) @Parameter(description = "Set of concept ids to match") Set<String> conceptIds,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {

		branch = BranchPathUriUtil.decodePath(branch);
		conceptIds = isEmpty(conceptIds) ? newHashSet() : conceptIds;
		if(isNotBlank(conceptId)) {
			conceptIds.add(conceptId);
		}
		return new ItemsPage<>(descriptionService.findDescriptions(branch, null, null, unmodifiableSet(conceptIds), ControllerHelper.getPageRequest(offset, limit)));
	}

	@GetMapping(value = "{branch}/descriptions/{descriptionId}")
	@JsonView(value = View.Component.class)
	public Description fetchDescription(@PathVariable String branch, @PathVariable String descriptionId) {
		return ControllerHelper.throwIfNotFound("Description", descriptionService.findDescription(BranchPathUriUtil.decodePath(branch), descriptionId));
	}

	@Operation(summary = "Delete a description.")
	@DeleteMapping(value = "{branch}/descriptions/{descriptionId}")
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	public void deleteDescription(
			@PathVariable String branch,
			@PathVariable String descriptionId,
			@Parameter(description = "Force the deletion of a released description.")
			@RequestParam(defaultValue = "false") boolean force) {
		branch = BranchPathUriUtil.decodePath(branch);
		Description description = ControllerHelper.throwIfNotFound("Description", descriptionService.findDescription(branch, descriptionId));
		descriptionService.deleteDescription(description, branch, force);
	}

	@Operation(summary = "List semantic tags of all active concepts together with a count of concepts using each.")
	@GetMapping(value = "{branch}/descriptions/semantictags")
	@JsonView(value = View.Component.class)
	public Map<String, Long> countSemanticTags(@PathVariable String branch) {

		branch = BranchPathUriUtil.decodePath(branch);
		return descriptionService.countActiveConceptsPerSemanticTag(branch);
	}

}
