package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.MultiSearchService;
import org.snomed.snowstorm.core.data.services.TooCostlyException;
import org.snomed.snowstorm.core.data.services.pojo.ConceptCriteria;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.rest.pojo.BrowserDescriptionSearchResult;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@Api(tags = "MultiSearch", description = "-")
@RequestMapping(produces = "application/json")
public class MultiSearchController {

	@Autowired
	private MultiSearchService multiSearchService;

	@Autowired
	private ConceptService conceptService;

	public enum ContentScope {
		ALL_PUBLISHED_CONTENT
	}

	@ApiOperation("Search descriptions across multiple Code Systems.")
	@RequestMapping(value = "multisearch/descriptions", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<BrowserDescriptionSearchResult> findDescriptions(
			@RequestParam String term,// Required!
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Collection<String> module,

			@ApiParam(value = "Set of two character language codes to match. " +
					"The English language code 'en' will not be added automatically, in contrast to the Accept-Language header which always includes it. " +
					"Accept-Language header still controls result FSN and PT language selection.")
			@RequestParam(required = false) Set<String> language,

			@ApiParam(value = "Set of description types to include. Pick descendants of '900000000000446008 | Description type (core metadata concept) |'.")
			@RequestParam(required = false) Set<Long> type,

			@RequestParam(required = false) Boolean conceptActive,
			@RequestParam(defaultValue = "ALL_PUBLISHED_CONTENT") ContentScope contentScope,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) throws TooCostlyException {

		TimerUtil timer = new TimerUtil("MultiSearch - Descriptions");

		DescriptionCriteria descriptionCriteria = new DescriptionCriteria()
				.term(term)
				.active(active)
				.modules(module)
				.searchLanguageCodes(language)
				.type(type)
				.conceptActive(conceptActive);

		PageRequest pageRequest = ControllerHelper.getPageRequest(offset, limit);
		Page<Description> descriptions = multiSearchService.findDescriptions(descriptionCriteria, pageRequest);
		timer.checkpoint("description search");

		Map<String, List<Description>> branchDescriptions = new HashMap<>();
		Map<String, List<String>> branchConceptIds = new HashMap<>();
		for (Description description : descriptions) {
			branchDescriptions.computeIfAbsent(description.getPath(), s -> new ArrayList<>()).add(description);
			branchConceptIds.computeIfAbsent(description.getPath(), s -> new ArrayList<>()).add(description.getConceptId());
		}

		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader);
		Map<String, ConceptMini> conceptMiniMap = new HashMap<>();
		for (String branchPath : branchConceptIds.keySet()) {
			conceptMiniMap.putAll(conceptService.findConceptMinis(branchPath, branchConceptIds.get(branchPath), languageDialects).getResultsMap());
			timer.checkpoint("Join concepts from " + branchPath);
		}

		List<BrowserDescriptionSearchResult> results = new ArrayList<>();
		for (Description description : descriptions) {
			BrowserDescriptionSearchResult result = new BrowserDescriptionSearchResult(description.getTerm(), description.isActive(), description.getLanguageCode(),
					description.getModuleId(), conceptMiniMap.get(description.getConceptId()));
			result.addExtraField("branchPath", description.getPath());
			results.add(result);
		}
		timer.finish();

		return new ItemsPage<>(new PageImpl<>(results, pageRequest, descriptions.getTotalElements()));
	}
	
	@ApiOperation("Search concepts across multiple Code Systems.")
	@RequestMapping(value = "multisearch/concepts", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<ConceptMini> findConcepts(
			@RequestParam(required = false) Set<String> conceptIds,
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) throws TooCostlyException {

		TimerUtil timer = new TimerUtil("MultiSearch - Concepts");
		ConceptCriteria conceptCriteria = new ConceptCriteria()
				.conceptIds(conceptIds)
				.active(active);

		PageRequest pageRequest = ControllerHelper.getPageRequest(offset, limit);
		Page<Concept> concepts = multiSearchService.findConcepts(conceptCriteria, pageRequest);
		List<ConceptMini> minis = concepts.getContent().stream().map(concept -> {
			ConceptMini mini = new ConceptMini(concept, null);
			mini.addExtraField("branch", concept.getPath());
			return mini;
		}).collect(Collectors.toList());
		timer.finish();

		return new ItemsPage<>(new PageImpl<>(minis, pageRequest, concepts.getTotalElements()));
	}

}
