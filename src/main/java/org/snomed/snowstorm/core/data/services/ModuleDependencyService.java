package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.util.SPathUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.stereotype.Service;

import java.util.*;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static org.snomed.snowstorm.core.data.domain.SnomedComponent.Fields.START;

@Service
public class ModuleDependencyService extends ComponentService {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModuleDependencyService.class);

	private final BranchService branchService;
	private final CodeSystemService codeSystemService;
	private final ReferenceSetMemberService referenceSetMemberService;
	private final ElasticsearchOperations elasticsearchOperations;
	private final VersionControlHelper versionControlHelper;
	private final SBranchService sBranchService;

	public ModuleDependencyService(BranchService branchService, CodeSystemService codeSystemService, @Lazy ReferenceSetMemberService referenceSetMemberService, ElasticsearchOperations elasticsearchOperations, VersionControlHelper versionControlHelper, SBranchService sBranchService) {
		this.branchService = branchService;
		this.codeSystemService = codeSystemService;
		this.referenceSetMemberService = referenceSetMemberService;
		this.elasticsearchOperations = elasticsearchOperations;
		this.versionControlHelper = versionControlHelper;
		this.sBranchService = sBranchService;
	}

	/**
	 * In preparation for starting a new authoring cycle, clear the sourceEffectiveTime and, optionally, the targetEffectiveTime
	 * of the relevant ReferenceSetMembers.
	 *
	 * @param branchPath The branch path to process.
	 * @return Whether the operation has completed successfully.
	 */
	public boolean clearSourceAndTargetEffectiveTimes(String branchPath) {
		if (branchPath == null || branchPath.isEmpty()) {
			LOGGER.error("Cannot update MDRS: cannot clear source and target effective times as the given branch path is null.");
			return false;
		}

		Branch branch = getBranch(branchPath);
		if (branch == null || branch.getMetadata() == null || branch.getMetadata().size() == 0) {
			LOGGER.error("Cannot update MDRS: cannot clear source and target effective times as {} has no metadata.", branchPath);
			return false;
		}

		Set<String> modules = sBranchService.getModules(branchPath);
		if (modules.isEmpty()) {
			LOGGER.error("Cannot update MDRS: cannot clear source and target effective times as {} has no modules.", branchPath);
			return false;
		}

		List<ReferenceSetMember> referenceSetMembers = getMDRSEntries(versionControlHelper.getBranchCriteria(branchPath), SPathUtil.getAncestry(branchPath), modules);
		if (referenceSetMembers.isEmpty()) {
			LOGGER.error("Cannot update MDRS: cannot clear source and target effective times as {} has no existing MDRS.", branchPath);
			return false;
		}

		List<String> membersUpdated = new ArrayList<>();
		for (ReferenceSetMember referenceSetMember : referenceSetMembers) {
			referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, null);

			boolean dependingOnOwnModule = modules.contains(referenceSetMember.getReferencedComponentId());
			if (dependingOnOwnModule) {
				referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, null);
			}

			referenceSetMember = referenceSetMemberService.updateMember(branchPath, referenceSetMember);
			membersUpdated.add(referenceSetMember.getMemberId());
		}

		LOGGER.info("{} MDRS entries prepared for new authoring cycle for {}:{}", membersUpdated.size(), branchPath, membersUpdated);
		return true;
	}

	/**
	 * In preparation for versioning, set the sourceEffectiveTime and, optionally, the targetEffectiveTime
	 * of the relevant ReferenceSetMembers.
	 *
	 * @param branchPath     The branch path to process.
	 * @param effectiveTimeI The soon-to-be new effectiveTime for the CodeSystem.
	 * @return Whether the operation has completed successfully.
	 */
	public boolean setSourceAndTargetEffectiveTimes(String branchPath, Integer effectiveTimeI) {
		Branch branch = getBranch(branchPath);
		if (branch == null || branch.getMetadata() == null || branch.getMetadata().size() == 0) {
			LOGGER.error("Cannot update MDRS: cannot set source and target effective times as the given branch path is null.");
			return false;
		}

		Set<String> modules = sBranchService.getModules(branchPath);
		if (modules.isEmpty()) {
			LOGGER.error("Cannot update MDRS: cannot set source and target effective times as {} has no modules.", branchPath);
			return false;
		}

		List<ReferenceSetMember> referenceSetMembers = getMDRSEntries(versionControlHelper.getBranchCriteria(branchPath), SPathUtil.getAncestry(branchPath), modules);
		if (referenceSetMembers.isEmpty()) {
			LOGGER.error("Cannot update MDRS: cannot set source and target effective times as {} has existing MDRS.", branchPath);
			return false;
		}

		List<String> membersUpdated = new ArrayList<>();
		String effectiveTime = String.valueOf(effectiveTimeI);
		for (ReferenceSetMember referenceSetMember : referenceSetMembers) {
			referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, effectiveTime);

			boolean dependingOnOwnModule = modules.contains(referenceSetMember.getReferencedComponentId());
			if (dependingOnOwnModule) {
				referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, effectiveTime);
			}

			referenceSetMember = referenceSetMemberService.updateMember(branchPath, referenceSetMember);
			membersUpdated.add(referenceSetMember.getMemberId());
		}

		LOGGER.info("{} MDRS entries prepared for versioning for {}:{}", membersUpdated.size(), branchPath, membersUpdated);
		return true;
	}

	/**
	 * In preparation for upgrading, set the targetEffectiveTime of the relevant ReferenceSetMembers.
	 *
	 * @param branchPath     The branch path to process.
	 * @param effectiveTimeI The effectiveTime for the CodeSystem the current branch path depends on.
	 * @return Whether the operation has completed successfully.
	 */
	public boolean setTargetEffectiveTime(String branchPath, Integer effectiveTimeI) {
		if (branchPath == null || branchPath.isEmpty()) {
			LOGGER.error("Cannot update MDRS: cannot set target effective time as the given branch path is null.");
			return false;
		}

		Optional<CodeSystem> codeSystem = codeSystemService.findByBranchPath(branchPath);
		if (codeSystem.isEmpty()) {
			LOGGER.error("Cannot update MDRS: cannot set target effective time as {} has no associated CodeSystem.", branchPath);
			return false;
		}

		if ("MAIN".equals(codeSystem.get().getBranchPath())) {
			LOGGER.error("Cannot update MDRS: root CodeSystem cannot be upgraded.");
			// Root CodeSystem cannot be upgraded.
			return false;
		}

		Set<String> modules = sBranchService.getModules(branchPath);
		if (modules.isEmpty()) {
			LOGGER.error("Cannot update MDRS: cannot set target effective time as {} has no modules.", branchPath);
			return false;
		}

		List<ReferenceSetMember> referenceSetMembers = getMDRSEntries(versionControlHelper.getBranchCriteria(branchPath), SPathUtil.getAncestry(branchPath), modules);
		if (referenceSetMembers.isEmpty()) {
			LOGGER.error("Cannot update MDRS: cannot set target effective time as {} has existing MDRS.", branchPath);
			return false;
		}

		List<String> membersUpdated = new ArrayList<>();
		String effectiveTime = String.valueOf(effectiveTimeI);
		for (ReferenceSetMember referenceSetMember : referenceSetMembers) {
			boolean dependingOnOwnModule = modules.contains(referenceSetMember.getReferencedComponentId());
			if (!dependingOnOwnModule) {
				referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, effectiveTime);
			}

			referenceSetMember = referenceSetMemberService.updateMember(branchPath, referenceSetMember);
			membersUpdated.add(referenceSetMember.getMemberId());
		}

		LOGGER.info("{} MDRS entries prepared for upgrading for {}:{}", membersUpdated.size(), branchPath, membersUpdated);
		return true;
	}

	private List<ReferenceSetMember> getMDRSEntries(BranchCriteria branchCriteria, List<String> ancestry, Set<String> modules) {
		List<ReferenceSetMember> referenceSetMembers = new ArrayList<>();
		try (SearchHitsIterator<ReferenceSetMember> hits = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
				.withQuery(bool(bq -> {
					bq
							.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
							.mustNot(existsQuery(SnomedComponent.Fields.END))
							.must(termsQuery(SnomedComponent.Fields.MODULE_ID, modules))
							.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.MODULE_DEPENDENCY_REFERENCE_SET));

					return bq;
				}))
				.withPageable(LARGE_PAGE)
				.build(), ReferenceSetMember.class)) {
			hits.forEachRemaining(hit -> {
				ReferenceSetMember referenceSetMember = hit.getContent();
				boolean belongsToCodeSystem = ancestry.contains(referenceSetMember.getPath());
				if (belongsToCodeSystem) {
					referenceSetMembers.add(referenceSetMember);
				}
			});
		}

		return referenceSetMembers;
	}

	private Branch getBranch(String branchPath) {
		try {
			return branchService.findBranchOrThrow(branchPath, true);
		} catch (BranchNotFoundException | IllegalArgumentException e) {
			return null;
		}
	}

	public Map<String, String> getCodeSystemBranchByModuleId(Set<String> moduleIds) {
		// Fetch the CodeSystem branch for a given module id using module dependency reference set
		Map<String, String> results = new HashMap<>();

		try (SearchHitsIterator<ReferenceSetMember> hits = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
				.withQuery(bool(bq -> bq
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
						.mustNot(existsQuery(SnomedComponent.Fields.END))
						.must(termsQuery(SnomedComponent.Fields.MODULE_ID, moduleIds))
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.MODULE_DEPENDENCY_REFERENCE_SET)))
				).withSort(SortOptions.of(s -> s.field(f -> f.field(START).order(SortOrder.Desc))))
				.withPageable(LARGE_PAGE)
				.build(), ReferenceSetMember.class)) {
			// Skip MDRS entries that have been imported into MAIN previously such as derivative products
			// Sorting by start date in descending order
			hits.forEachRemaining(hit -> results.putIfAbsent(hit.getContent().getModuleId(), hit.getContent().getPath())
			);
		}
		return results;
	}
}