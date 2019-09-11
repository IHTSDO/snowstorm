package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import org.snomed.snowstorm.core.data.services.AuthoringStatsService;
import org.snomed.snowstorm.core.data.services.pojo.AuthoringStatsSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "Authoring Stats", description = "-")
@RequestMapping(produces = "application/json")
public class AuthoringStatsController {

	@Autowired
	private AuthoringStatsService authoringStatsService;

	@RequestMapping(value = "{branch}/authoring-stats", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public AuthoringStatsSummary getStats(@PathVariable String branch) {
		branch = BranchPathUriUtil.decodePath(branch);
		return authoringStatsService.getStats(branch);
	}

}
