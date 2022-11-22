package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMicro;
import org.snomed.snowstorm.core.data.domain.DescriptionMicro;
import org.snomed.snowstorm.core.data.services.AuthoringStatsService;
import org.snomed.snowstorm.core.data.services.pojo.AuthoringStatsSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Authoring Stats", description = "-")
@RequestMapping(produces = "application/json")
public class AuthoringStatsController {

	@Autowired
	private AuthoringStatsService authoringStatsService;

	@Operation(summary = "Calculate statistics for unreleased/unversioned content to be used in daily build browser.", description = "Does not work on versioned content.")
	@GetMapping(value = "{branch}/authoring-stats", produces = "application/json")
	public AuthoringStatsSummary getStats(@PathVariable String branch) {
		branch = BranchPathUriUtil.decodePath(branch);
		return authoringStatsService.getStats(branch);
	}
	
	@Operation(summary = "Get counts of various components types per module id")
	@GetMapping(value = "{branch}/authoring-stats/module-counts", produces = "application/json")
	public Map<String, Map<String, Long>> getPerModuleCounts(@PathVariable String branch) {
		branch = BranchPathUriUtil.decodePath(branch);
		return authoringStatsService.getComponentCountsPerModule(branch);
	}

	@GetMapping(value = "{branch}/authoring-stats/new-concepts", produces = "application/json")
	public List<ConceptMicro> getNewConcepts(
			@PathVariable String branch,
			@RequestParam(required = false, defaultValue = "false") boolean unpromotedChangesOnly,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getNewConcepts(BranchPathUriUtil.decodePath(branch), unpromotedChangesOnly, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}
	
	@GetMapping(value = "{branch}/authoring-stats/new-descriptions", produces = "application/json")
	public List<DescriptionMicro> getNewDescriptions(
			@PathVariable String branch,
			@RequestParam(required = false, defaultValue = "false") boolean unpromotedChangesOnly,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getNewDescriptions(BranchPathUriUtil.decodePath(branch), unpromotedChangesOnly, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@GetMapping(value = "{branch}/authoring-stats/inactivated-concepts", produces = "application/json")
	public List<ConceptMicro> getInactivatedConcepts(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getInactivatedConcepts(BranchPathUriUtil.decodePath(branch), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@GetMapping(value = "{branch}/authoring-stats/reactivated-concepts", produces = "application/json")
	public List<ConceptMicro> getReactivatedConcepts(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getReactivatedConcepts(BranchPathUriUtil.decodePath(branch), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@GetMapping(value = "{branch}/authoring-stats/changed-fully-specified-names", produces = "application/json")
	public List<ConceptMicro> getChangedFSNs(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getChangedFSNs(BranchPathUriUtil.decodePath(branch), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@GetMapping(value = "{branch}/authoring-stats/inactivated-synonyms", produces = "application/json")
	public List<ConceptMicro> getInactivatedSynonyms(@PathVariable String branch) {
		return authoringStatsService.getInactivatedSynonyms(BranchPathUriUtil.decodePath(branch));
	}

	@GetMapping(value = "{branch}/authoring-stats/new-synonyms-on-existing-concepts", produces = "application/json")
	public List<ConceptMicro> getNewSynonymsOnExistingConcepts(@PathVariable String branch) {
		return authoringStatsService.getNewSynonymsOnExistingConcepts(BranchPathUriUtil.decodePath(branch));
	}

	@GetMapping(value = "{branch}/authoring-stats/reactivated-synonyms", produces = "application/json")
	public List<ConceptMicro> getReactivatedSynonyms(@PathVariable String branch) {
		return authoringStatsService.getReactivatedSynonyms(BranchPathUriUtil.decodePath(branch));
	}

}
