package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMicro;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.DescriptionMicro;
import org.snomed.snowstorm.core.data.services.AuthoringStatsService;
import org.snomed.snowstorm.core.data.services.pojo.AuthoringStatsSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Api(tags = "Authoring Stats", description = "-")
@RequestMapping(produces = "application/json")
public class AuthoringStatsController {

	@Autowired
	private AuthoringStatsService authoringStatsService;

	@ApiOperation(value = "Calculate statistics for unreleased/unversioned content to be used in daily build browser.", notes = "Does not work on versioned content.")
	@RequestMapping(value = "{branch}/authoring-stats", method = RequestMethod.GET, produces = "application/json")
	public AuthoringStatsSummary getStats(@PathVariable String branch) {
		branch = BranchPathUriUtil.decodePath(branch);
		return authoringStatsService.getStats(branch);
	}
	
	@ApiOperation(value = "Get counts of various components types per module id")
	@RequestMapping(value = "{branch}/authoring-stats/module-counts", method = RequestMethod.GET, produces = "application/json")
	public Map<String, Map<String, Long>> getPerModuleCounts(@PathVariable String branch) {
		branch = BranchPathUriUtil.decodePath(branch);
		return authoringStatsService.getComponentCountsPerModule(branch);
	}

	@RequestMapping(value = "{branch}/authoring-stats/new-concepts", method = RequestMethod.GET, produces = "application/json")
	public List<ConceptMicro> getNewConcepts(
			@PathVariable String branch,
			@RequestParam(required = false, defaultValue = "false") boolean unpromotedChangesOnly,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getNewConcepts(BranchPathUriUtil.decodePath(branch), unpromotedChangesOnly, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}
	
	@RequestMapping(value = "{branch}/authoring-stats/new-descriptions", method = RequestMethod.GET, produces = "application/json")
	public List<DescriptionMicro> getNewDescriptions(
			@PathVariable String branch,
			@RequestParam(required = false, defaultValue = "false") boolean unpromotedChangesOnly,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getNewDescriptions(BranchPathUriUtil.decodePath(branch), unpromotedChangesOnly, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@RequestMapping(value = "{branch}/authoring-stats/inactivated-concepts", method = RequestMethod.GET, produces = "application/json")
	public List<ConceptMicro> getInactivatedConcepts(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getInactivatedConcepts(BranchPathUriUtil.decodePath(branch), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@RequestMapping(value = "{branch}/authoring-stats/reactivated-concepts", method = RequestMethod.GET, produces = "application/json")
	public List<ConceptMicro> getReactivatedConcepts(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getReactivatedConcepts(BranchPathUriUtil.decodePath(branch), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@RequestMapping(value = "{branch}/authoring-stats/changed-fully-specified-names", method = RequestMethod.GET, produces = "application/json")
	public List<ConceptMicro> getChangedFSNs(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getChangedFSNs(BranchPathUriUtil.decodePath(branch), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@RequestMapping(value = "{branch}/authoring-stats/inactivated-synonyms", method = RequestMethod.GET, produces = "application/json")
	public List<ConceptMicro> getInactivatedSynonyms(@PathVariable String branch) {
		return authoringStatsService.getInactivatedSynonyms(BranchPathUriUtil.decodePath(branch));
	}

	@RequestMapping(value = "{branch}/authoring-stats/new-synonyms-on-existing-concepts", method = RequestMethod.GET, produces = "application/json")
	public List<ConceptMicro> getNewSynonymsOnExistingConcepts(@PathVariable String branch) {
		return authoringStatsService.getNewSynonymsOnExistingConcepts(BranchPathUriUtil.decodePath(branch));
	}

	@RequestMapping(value = "{branch}/authoring-stats/reactivated-synonyms", method = RequestMethod.GET, produces = "application/json")
	public List<ConceptMicro> getReactivatedSynonyms(@PathVariable String branch) {
		return authoringStatsService.getReactivatedSynonyms(BranchPathUriUtil.decodePath(branch));
	}

}
