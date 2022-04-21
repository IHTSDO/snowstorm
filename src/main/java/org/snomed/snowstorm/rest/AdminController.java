package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.traceability.TraceabilityLogBackfiller;
import org.snomed.snowstorm.ecl.BranchVersionECLCache;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.fix.ContentFixService;
import org.snomed.snowstorm.fix.ContentFixType;
import org.snomed.snowstorm.fix.TechnicalFixService;
import org.snomed.snowstorm.fix.TechnicalFixType;
import org.snomed.snowstorm.mrcm.MRCMUpdateService;
import org.snomed.snowstorm.rest.pojo.UpdatedDocumentCount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@Tag(name = "Admin", description = "-")
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

	@Autowired
	private SBranchService sBranchService;

	@Autowired
	private TraceabilityLogBackfiller traceabilityLogBackfiller;

	@Autowired
	private ContentFixService contentFixService;

	@Autowired
	private TechnicalFixService technicalFixService;

	@Autowired
	private ECLQueryService eclQueryService;

	@Operation(summary = "Rebuild the description index.",
			description = "Use this if the search configuration for international character handling of a language has been " +
					"set or updated after importing content of that language. " +
					"The descriptions of the specified language will be reindexed on all branches using the new configuration. " +
					"N.B. Snowstorm must be restarted to read the new configuration.")
	@PostMapping(value = "/actions/rebuild-description-index-for-language")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void rebuildDescriptionIndexForLanguage(@RequestParam String languageCode) throws IOException {
		ControllerHelper.requiredParam(languageCode, "languageCode");
		adminOperationsService.reindexDescriptionsForLanguage(languageCode);
	}

	@Operation(summary = "Backfill traceability information.",
			description = "Used to backfill data after upgrading to Traceability Service version 3.1.x. " +
					"Sends previously missing information to the Traceability Service including the commit date of all code system versions.")
	@PostMapping(value = "/actions/traceability-backfill")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void traceabilityBackfill(@RequestParam(required = false) Long sinceEpochMillisecondDate) {
		Date sinceDate = sinceEpochMillisecondDate != null ? new Date(sinceEpochMillisecondDate) : null;
		traceabilityLogBackfiller.run(sinceDate);
	}

	@Operation(summary = "Rebuild the semantic index of the branch.",
			description = "You are unlikely to need this action. " +
					"If something has gone wrong with processing of content updates on the branch then semantic index, " +
					"which supports the ECL queries, can be rebuilt on demand. \n" +
					"Setting the dryRun to true when rebuilding the 'MAIN' branch will log a summary of the changes required without persisting the changes. This " +
					"parameter can not be used on other branches. \n" +
					"If no changes are required or dryRun is set the empty commit used to run this function will be rolled back.")
	@PostMapping(value = "/{branch}/actions/rebuild-semantic-index")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public UpdatedDocumentCount rebuildBranchTransitiveClosure(@PathVariable String branch, @RequestParam(required = false, defaultValue = "false") boolean dryRun)
			throws ServiceException {

		final Map<String, Integer> updateCount = queryConceptUpdateService.rebuildStatedAndInferredSemanticIndex(BranchPathUriUtil.decodePath(branch), dryRun);
		return new UpdatedDocumentCount(updateCount);
	}

	@Operation(summary = "Force update of definition statuses of all concepts based on axioms.",
			description = "You are unlikely to need this action. " +
					"If something has wrong with processing content updates on the branch the definition statuses " +
					"of all concepts can be updated based on the concept's axioms. ")
	@PostMapping(value = "/{branch}/actions/update-definition-statuses")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void updateDefinitionStatuses(@PathVariable String branch) throws ServiceException {
		definitionStatusUpdateService.updateAllDefinitionStatuses(BranchPathUriUtil.decodePath(branch));
	}

	@Operation(summary = "End duplicate versions of donated components in version control.",
			description = "You may need this action if you have used the branch merge operation to upgrade an extension " +
					"which has donated content to the International Edition. The operation should be run on the extension branch.")
	@PostMapping(value = "/{branch}/actions/end-donated-content")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public Map<String, Object> endDonatedContent(@PathVariable String branch) {
		Map<String, Object> response = new HashMap<>();
		Map<Class, Set<String>> fixes = adminOperationsService.findAndEndDonatedContent(BranchPathUriUtil.decodePath(branch));
		response.put("fixesApplied", fixes);
		return response;
	}

	@Operation(summary = "Hide parent version of duplicate versions of components in version control.")
	@PostMapping(value = "/{branch}/actions/find-duplicate-hide-parent-version")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public Map<String, Object> findDuplicateAndHideParentVersion(@PathVariable String branch) {
		Map<String, Object> response = new HashMap<>();
		Map<Class, Set<String>> fixes = adminOperationsService.findDuplicateAndHideParentVersion(BranchPathUriUtil.decodePath(branch));
		response.put("fixesApplied", fixes);
		return response;
	}

	@Operation(summary = "Remove any redundant entries from the versions replaced map on a branch in version control.")
	@PostMapping(value = "/{branch}/actions/remove-redundant-versions-replaced")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public Map<String, Object> reduceVersionsReplaced(@PathVariable String branch) {
		Map<String, Object> response = new HashMap<>();
		Map<Class, AtomicLong> fixes = adminOperationsService.reduceVersionsReplaced(BranchPathUriUtil.decodePath(branch));
		response.put("fixesApplied", fixes);
		return response;
	}

	@Operation(summary = "Rollback a commit on a branch.",
			description = "Use with caution! This operation only permits rolling back the latest commit on a branch. " +
					"If there are any child branches they should be manually deleted or rebased straight after rollback. \n" +
					"If the commit being rolled back created a code system version and release branch then they will be deleted automatically as part of rollback."
	)
	@PostMapping(value = "/{branch}/actions/rollback-commit")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void rollbackCommit(@PathVariable String branch, @RequestParam long commitHeadTime) {
		sBranchService.rollbackCommit(BranchPathUriUtil.decodePath(branch), commitHeadTime);
	}

	@Operation(summary = "Rollback a partial commit on a branch.",
			description = "Use with extreme caution! Only rollback a partial commit which you know has failed and is no longer in progress."
	)
	@PostMapping(value = "/{branch}/actions/rollback-partial-commit")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void rollbackPartialCommit(@PathVariable String branch) {
		sBranchService.rollbackPartialCommit(BranchPathUriUtil.decodePath(branch));
	}

	@Operation(summary = "Hard delete a branch including its content and history.",
			description = "This function is not usually needed but can be used to remove a branch which needs to be recreated with the same path. " +
					"Everything will be wiped out including all the content (which is on the branch and has not yet been promoted to the parent branch) " +
					"and the branch history (previous versions of the content in version control). " +
					"This function only works on branches with no children. "
	)
	@DeleteMapping(value = "/{branch}/actions/hard-delete")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void hardDeleteBranch(@PathVariable String branch) {
		adminOperationsService.hardDeleteBranch(BranchPathUriUtil.decodePath(branch));
	}

	@Operation(summary = "Restore role group number of inactive relationships.")
	@PostMapping(value = "/{branch}/actions/inactive-relationships-restore-group-number")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void restoreGroupNumberOfInactiveRelationships(@PathVariable String branch, @RequestParam String currentEffectiveTime, @RequestParam String previousReleaseBranch) {
		adminOperationsService.restoreGroupNumberOfInactiveRelationships(BranchPathUriUtil.decodePath(branch), currentEffectiveTime, previousReleaseBranch);
	}

	@Operation(summary = "Delete inferred relationships which are NOT in the provided file.",
			description = "This function will delete all inferred relationships found on the specified branch where the id is NOT in the snapshot RF2 relationship file provided. " +
					"This can be useful to help clean up differences between an Alpha/Beta/Member extension release and the final release if both have been imported.")
	@PostMapping(value = "/{branch}/actions/delete-extra-inferred-relationships", consumes = "multipart/form-data")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void deleteExtraInferredRelationships(@PathVariable String branch, @RequestParam int effectiveTime, @RequestParam MultipartFile relationshipsToKeep) {
		try {
			adminOperationsService.deleteExtraInferredRelationships(BranchPathUriUtil.decodePath(branch), relationshipsToKeep.getInputStream(), effectiveTime);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to read/process the file provided.");
		}
	}

	@Operation(summary = "Promote release fix to Code System branch.",
			description = "In an authoring terminology server; if small content changes have to be made to a code system version after it's been created " +
					"the changes can be applied to the release branch and then merged back to the main code system branch using this function. " +
					"This function performs the following steps: " +
					"1. The changes are merged to the parent branch at a point in time immediately after the latest version was created. " +
					"2. The version branch is rebased to this new commit. " +
					"3. A second commit is made at a point in time immediately after the fix commit to revert the changes. " +
					"This is necessary to preserve the integrity of more recent commits on the code system branch made during the new ongoing authoring cycle. " +
					"The fixes should be applied to head timepoint of the code system branch using an alternative method. "
	)
	@PostMapping(value = "/{releaseFixBranch}/actions/promote-release-fix")
	@PreAuthorize("hasPermission('ADMIN', #releaseFixBranch)")
	public void promoteReleaseFix(@PathVariable String releaseFixBranch) {
		adminOperationsService.promoteReleaseFix(BranchPathUriUtil.decodePath(releaseFixBranch));
	}

	@PostMapping(value = "/{branch}/actions/clone-child-branch")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void cloneChildBranch(@PathVariable String branch, @RequestParam String newBranch) {
		adminOperationsService.cloneChildBranch(BranchPathUriUtil.decodePath(branch), newBranch);
	}

	@Operation(summary = "Force update of MRCM domain templates and MRCM attribute rules.",
			description = "You are unlikely to need this action. " +
					"If something has gone wrong when editing MRCM reference sets you can use this function to force updating the domain templates and attribute rules " +
					"for all MRCM reference components.")
	@PostMapping(value = "/{branch}/actions/update-mrcm-domain-templates-and-attribute-rules")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void updateMRCMDomainTemplatesAndAttributeRules(@PathVariable String branch) throws ServiceException {
		mrcmUpdateService.updateAllDomainTemplatesAndAttributeRules(BranchPathUriUtil.decodePath(branch));
	}

	@Operation(summary = "Find concepts in the semantic index which should not be there. The concept may be inactive or deleted. To catch and debug rare cases.")
	@PostMapping(value = "/{branch}/actions/find-extra-concepts-in-semantic-index")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public IntegrityService.ConceptsInForm findExtraConceptsInSemanticIndex(@PathVariable String branch) {
		return integrityService.findExtraConceptsInSemanticIndex(BranchPathUriUtil.decodePath(branch));
	}

	@Operation(summary = "Restore the 'released' flag and other fields of a concept.",
			description = "Restore the 'released' flag as well as the internal fields 'effectiveTimeI' and 'releaseHash' of all components of a concept. " +
					"Makes a new commit on the specified branch. Will restore any deleted components as inactive. " +
					"Looks up the code system, latest release branch and any dependant release branch automatically. ")
	@PostMapping(value = "/{branch}/actions/restore-released-status")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void restoreReleasedStatus(@PathVariable String branch, @RequestParam Set<String> conceptIds,
				@RequestParam(defaultValue = "true") boolean setDeletedComponentsToInactive) {

		adminOperationsService.restoreReleasedStatus(BranchPathUriUtil.decodePath(branch), conceptIds, setDeletedComponentsToInactive);
	}

	@Operation(summary = "Clean newly inactive inferred relationships during authoring.",
			description = "The previous release and dependant release (if applicable) branches are considered.\n\n" +
					"For inactive inferred relationships with no effectiveTime:\n\n" +
					" - if they were already inactive then restore that version\n\n" +
					" - if they did not previously exist then delete them")
	@PostMapping(value = "/{branch}/actions/clean-inferred")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void cleanInferredRelationships(@PathVariable String branch) {
		adminOperationsService.cleanInferredRelationships(BranchPathUriUtil.decodePath(branch));
	}

	@PostMapping(value = "/{branch}/actions/content-fix")
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	public void runContentFix(@PathVariable String branch, @RequestParam ContentFixType contentFixType, @RequestParam Set<Long> conceptIds) {
		contentFixService.runContentFix(BranchPathUriUtil.decodePath(branch), contentFixType, conceptIds);
	}

	@PostMapping(value = "/{branch}/actions/technical-fix")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	@ResponseBody
	public String runTechnicalFix(@PathVariable String branch, @RequestParam TechnicalFixType technicalFixType) {
		return technicalFixService.runTechnicalFix(technicalFixType, BranchPathUriUtil.decodePath(branch));
	}

	@GetMapping(value = "/cache/ecl/stats")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public Map<String, Map<String, Long>> getECLCacheStats() {
		final Map<String, BranchVersionECLCache> cacheMap = eclQueryService.getResultsCache().getCacheMap();
		Map<String, Map<String, Long>> stats = new LinkedHashMap<>();
		for (String branch : new TreeSet<>(cacheMap.keySet())) {
			stats.put(branch, cacheMap.get(branch).getStats());
		}
		return stats;
	}

	@PostMapping(value = "/cache/ecl/clear")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void clearEclCache() {
		eclQueryService.clearCache();
	}

}
