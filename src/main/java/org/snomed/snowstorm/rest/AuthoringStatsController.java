package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.domain.ConceptMicro;
import org.snomed.snowstorm.core.data.services.AuthoringStatsService;
import org.snomed.snowstorm.core.data.services.pojo.AuthoringStatsSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Api(tags = "Authoring Stats", description = "-")
@RequestMapping(produces = "application/json")
public class AuthoringStatsController {

	@Autowired
	private AuthoringStatsService authoringStatsService;

	@ApiOperation(value = "Calculate statistics for unreleased/unversioned content to be used in daily build browser.", notes = "Does not work on versioned content.")
	@RequestMapping(value = "{branch}/authoring-stats", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public AuthoringStatsSummary getStats(@PathVariable String branch) {
		branch = BranchPathUriUtil.decodePath(branch);
		return authoringStatsService.getStats(branch);
	}

	@RequestMapping(value = "{branch}/authoring-stats/new-concepts", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public List<ConceptMicro> getNewConcepts(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getNewConcepts(BranchPathUriUtil.decodePath(branch), ControllerHelper.getLanguageCodes(acceptLanguageHeader));
	}

	@RequestMapping(value = "{branch}/authoring-stats/inactivated-concepts", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public List<ConceptMicro> getInactivatedConcepts(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getInactivatedConcepts(BranchPathUriUtil.decodePath(branch), ControllerHelper.getLanguageCodes(acceptLanguageHeader));
	}

	@RequestMapping(value = "{branch}/authoring-stats/reactivated-concepts", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public List<ConceptMicro> getReactivatedConcepts(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getReactivatedConcepts(BranchPathUriUtil.decodePath(branch), ControllerHelper.getLanguageCodes(acceptLanguageHeader));
	}

	@RequestMapping(value = "{branch}/authoring-stats/changed-fully-specified-names", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public List<ConceptMicro> getChangedFSNs(
			@PathVariable String branch,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return authoringStatsService.getChangedFSNs(BranchPathUriUtil.decodePath(branch), ControllerHelper.getLanguageCodes(acceptLanguageHeader));
	}

	@RequestMapping(value = "{branch}/authoring-stats/inactivated-synonyms", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public List<ConceptMicro> getInactivatedSynonyms(@PathVariable String branch) {
		return authoringStatsService.getInactivatedSynonyms(BranchPathUriUtil.decodePath(branch));
	}

}
