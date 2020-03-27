package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.mrcm.MRCMUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

	@Autowired
	private MRCMUpdateService mrcmUpdateService;

	@Autowired
	private IntegrityService integrityService;

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

	@ApiOperation(value = "Hide parent version of duplicate versions of components in version control.")
	@RequestMapping(value = "/{branch}/actions/find-duplicate-hide-parent-version", method = RequestMethod.POST)
	@ResponseBody
	public Map<String, Object> findDuplicateAndHideParentVersion(@PathVariable String branch) {
		Map<String, Object> response = new HashMap<>();
		Map<Class, Set<String>> fixes = adminOperationsService.findDuplicateAndHideParentVersion(BranchPathUriUtil.decodePath(branch));
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

	@ApiOperation(value = "Hard delete a branch including its content and history.",
			notes = "This function is not usually needed but can be used to remove a branch which needs to be recreated with the same path. " +
					"Everything will be wiped out including all the content (which is on the branch and has not yet been promoted to the parent branch) " +
					"and the branch history (previous versions of the content in version control). " +
					"This function only works on branches with no children. "
	)
	@RequestMapping(value = "/{branch}/actions/hard-delete", method = RequestMethod.DELETE)
	public void hardDeleteBranch(@PathVariable String branch) {
		adminOperationsService.hardDeleteBranch(BranchPathUriUtil.decodePath(branch));
	}

	@ApiOperation(value = "Restore role group number of inactive relationships.")
	@RequestMapping(value = "/{branch}/actions/inactive-relationships-restore-group-number", method = RequestMethod.POST)
	public void restoreGroupNumberOfInactiveRelationships(@PathVariable String branch, @RequestParam String currentEffectiveTime, @RequestParam String previousReleaseBranch) {
		adminOperationsService.restoreGroupNumberOfInactiveRelationships(BranchPathUriUtil.decodePath(branch), currentEffectiveTime, previousReleaseBranch);
	}

	@ApiOperation(value = "Delete inferred relationships which are NOT in the provided file.",
			notes = "This function will delete all inferred relationships found on the specified branch where the id is NOT in the snapshot RF2 relationship file provided. " +
					"This can be useful to help clean up differences between an Alpha/Beta/Member extension release and the final release if both have been imported.")
	@RequestMapping(value = "/{branch}/actions/delete-extra-inferred-relationships", method = RequestMethod.POST, consumes = "multipart/form-data")
	public void deleteExtraInferredRelationships(@PathVariable String branch, @RequestParam int effectiveTime, @RequestParam MultipartFile relationshipsToKeep) {
		try {
			adminOperationsService.deleteExtraInferredRelationships(BranchPathUriUtil.decodePath(branch), relationshipsToKeep.getInputStream(), effectiveTime);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to read/process the file provided.");
		}
	}

	@ApiOperation(value = "Promote release fix to Code System branch.",
			notes = "In an authoring terminology server; if small content changes have to be made to a code system version after it's been created " +
					"the changes can be applied to the release branch and then merged back to the main code system branch using this function. " +
					"This function performs the following steps: " +
					"1. The changes are merged to the parent branch at a point in time immediately after the latest version was created. " +
					"2. The version branch is rebased to this new commit. " +
					"3. A second commit is made at a point in time immediately after the fix commit to revert the changes. " +
					"This is necessary to preserve the integrity of more recent commits on the code system branch made during the new ongoing authoring cycle. " +
					"The fixes should be applied to head timepoint of the code system branch using an alternative method. "
	)
	@RequestMapping(value = "/{releaseFixBranch}/actions/promote-release-fix", method = RequestMethod.POST)
	public void promoteReleaseFix(@PathVariable String releaseFixBranch) {
		adminOperationsService.promoteReleaseFix(BranchPathUriUtil.decodePath(releaseFixBranch));
	}

	@RequestMapping(value = "/{branch}/actions/clone-child-branch", method = RequestMethod.POST)
	@ResponseBody
	public void cloneChildBranch(@PathVariable String branch, @RequestParam String newBranch) {
		adminOperationsService.cloneChildBranch(BranchPathUriUtil.decodePath(branch), newBranch);
	}

	@ApiOperation(value = "Force update of MRCM domain templates and MRCM attribute rules.",
			notes = "You are unlikely to need this action. " +
					"If something has gone wrong when editing MRCM reference sets you can use this function to force updating the domain templates and attribute rules " +
					"for all MRCM reference components.")
	@RequestMapping(value = "/{branch}/actions/update-mrcm-domain-templates-and-attribute-rules", method = RequestMethod.POST)
	@ResponseBody
	public void updateMRCMDomainTemplatesAndAttributeRules(@PathVariable String branch) throws ServiceException {
		mrcmUpdateService.updateAllDomainTemplatesAndAttributeRules(BranchPathUriUtil.decodePath(branch));
	}

	@ApiOperation("Find concepts in the semantic index which should not be there. The concept may be inactive or deleted. To catch and debug rare cases.")
	@RequestMapping(value = "/{branch}/actions/find-extra-concepts-in-semantic-index", method = RequestMethod.POST)
	@ResponseBody
	public IntegrityService.ConceptsInForm findExtraConceptsInSemanticIndex(@PathVariable String branch) {
		return integrityService.findExtraConceptsInSemanticIndex(BranchPathUriUtil.decodePath(branch));
	}

}
