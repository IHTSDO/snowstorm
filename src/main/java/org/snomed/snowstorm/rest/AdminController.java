package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.services.AdminOperationsService;
import org.snomed.snowstorm.core.data.services.ConceptDefinitionStatusUpdateService;
import org.snomed.snowstorm.core.data.services.SemanticIndexUpdateService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@Api(tags = "Admin", description = "-")
@RequestMapping(value = "/admin", produces = "application/json")
public class AdminController {

	@Autowired
	private SemanticIndexUpdateService queryConceptUpdateService;

	@Autowired
	private ConceptDefinitionStatusUpdateService definitionStatusUpdateService;

	@Autowired
	private AdminOperationsService adminOperationsService;

	@ApiOperation(value = "Rebuild the description index.",
			notes = "Use this if the search configuration for international character handling of a language has been " +
					"set or updated after importing content of that language. " +
					"The descriptions of the specified language will be reindexed on all branches using the new configuration. " +
					"N.B. Snowstorm must be restarted to read the new configuration.")
	@RequestMapping(value = "/actions/rebuild-description-index-for-language", method = RequestMethod.POST)
	public void rebuildDescriptionIndexForLanguage(@RequestParam String languageCode) throws IOException {
		ControllerHelper.requiredParam(languageCode, "languageCode");
		adminOperationsService.reindexDescriptionsForLanguage(languageCode);
	}

	@ApiOperation(value = "Rebuild the semantic index of the branch.",
			notes = "You are unlikely to need this action. " +
					"If something has gone wrong with processing of content updates on the branch then semantic index " +
					"which supports the ECL queries can be rebuilt on demand. " +
					"You are unlikely to need this action.")
	@RequestMapping(value = "/{branch}/actions/rebuild-semantic-index", method = RequestMethod.POST)
	public void rebuildBranchTransitiveClosure(@PathVariable String branch) throws ServiceException {
		queryConceptUpdateService.rebuildStatedAndInferredSemanticIndex(BranchPathUriUtil.decodePath(branch));
	}

	@ApiOperation(value = "Force update of definition statuses of all concepts based on axioms.",
			notes = "You are unlikely to need this action. " +
					"If something has wrong with processing content updates on the branch the definition statuses " +
					"of all concepts can be updated based on the concept's axioms. " +
					"You are unlikely to need this action.")
	@RequestMapping(value = "/{branch}/actions/update-definition-statuses", method = RequestMethod.POST)
	@ResponseBody
	public void updateDefinitionStatuses(@PathVariable String branch) throws ServiceException {
		definitionStatusUpdateService.updateAllDefinitionStatuses(BranchPathUriUtil.decodePath(branch));
	}

	@ApiOperation(value = "End duplicate versions of donated components in version control.",
			notes = "You may need this action if you have used the branch merge operation to upgrade an extension " +
					"which has donated content to the International Edition. The operation should be run on the extension branch.")
	@RequestMapping(value = "/{branch}/actions/end-donated-content", method = RequestMethod.POST)
	@ResponseBody
	public Map<String, Object> endDonatedContent(@PathVariable String branch) {
		Map<String, Object> response = new HashMap<>();
		Map<Class, Set<String>> fixes = adminOperationsService.findAndEndDonatedContent(BranchPathUriUtil.decodePath(branch));
		response.put("fixesApplied", fixes);
		return response;
	}

	@ApiOperation(value = "Rollback a commit on a branch.",
			notes = "Use with extreme caution! Only rollback a commit which you know is the latest commit on the branch " +
					"and that there are no child branches created or rebased since the commit otherwise version control will break."
	)
	@RequestMapping(value = "/{branch}/actions/rollback-commit", method = RequestMethod.POST)
	public void rollbackCommit(@PathVariable String branch, @RequestParam long commitHeadTime) {
		adminOperationsService.rollbackCommit(BranchPathUriUtil.decodePath(branch), commitHeadTime);
	}

	@ApiOperation(value = "Restore role group number of inactive relationships.")
	@RequestMapping(value = "/{branch}/actions/inactive-relationships-restore-group-number", method = RequestMethod.POST)
	public void restoreGroupNumberOfInactiveRelationships(@PathVariable String branch, @RequestParam String currentEffectiveTime, @RequestParam String previousReleaseBranch) {
		adminOperationsService.restoreGroupNumberOfInactiveRelationships(BranchPathUriUtil.decodePath(branch), currentEffectiveTime, previousReleaseBranch);
	}

}
