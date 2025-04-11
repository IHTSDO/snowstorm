package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Branch;
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

@Service
public class ModuleDependencyService extends ComponentService {
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
			return false;
		}

		Branch branch = getBranch(branchPath);
		if (branch == null || branch.getMetadata() == null || branch.getMetadata().size() == 0) {
			return false;
		}

		Set<String> modules = sBranchService.getModules(branchPath);
		if (modules.isEmpty()) {
			return false;
		}

		List<ReferenceSetMember> referenceSetMembers = getMDRSEntries(versionControlHelper.getBranchCriteria(branchPath), SPathUtil.getAncestry(branchPath), modules);
		if (referenceSetMembers.isEmpty()) {
			return false;
		}

		for (ReferenceSetMember referenceSetMember : referenceSetMembers) {
			referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, null);

			boolean dependingOnOwnModule = modules.contains(referenceSetMember.getReferencedComponentId());
			if (dependingOnOwnModule) {
				referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, null);
			}

			referenceSetMemberService.updateMember(branchPath, referenceSetMember);
		}

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
			return false;
		}

		Set<String> modules = sBranchService.getModules(branchPath);
		if (modules.isEmpty()) {
			return false;
		}

		List<ReferenceSetMember> referenceSetMembers = getMDRSEntries(versionControlHelper.getBranchCriteria(branchPath), SPathUtil.getAncestry(branchPath), modules);
		if (referenceSetMembers.isEmpty()) {
			return false;
		}

		String effectiveTime = String.valueOf(effectiveTimeI);
		for (ReferenceSetMember referenceSetMember : referenceSetMembers) {
			referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, effectiveTime);

			boolean dependingOnOwnModule = modules.contains(referenceSetMember.getReferencedComponentId());
			if (dependingOnOwnModule) {
				referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, effectiveTime);
			}

			referenceSetMemberService.updateMember(branchPath, referenceSetMember);
		}

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
			return false;
		}

		Optional<CodeSystem> codeSystem = codeSystemService.findByBranchPath(branchPath);
		if (codeSystem.isEmpty()) {
			return false;
		}

		if ("MAIN".equals(codeSystem.get().getBranchPath())) {
			// Root CodeSystem cannot be upgraded.
			return false;
		}

		Set<String> modules = sBranchService.getModules(branchPath);
		if (modules.isEmpty()) {
			return false;
		}

		List<ReferenceSetMember> referenceSetMembers = getMDRSEntries(versionControlHelper.getBranchCriteria(branchPath), SPathUtil.getAncestry(branchPath), modules);
		if (referenceSetMembers.isEmpty()) {
			return false;
		}

		String effectiveTime = String.valueOf(effectiveTimeI);
		for (ReferenceSetMember referenceSetMember : referenceSetMembers) {
			boolean dependingOnOwnModule = modules.contains(referenceSetMember.getReferencedComponentId());
			if (!dependingOnOwnModule) {
				referenceSetMember.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, effectiveTime);
			}

			referenceSetMemberService.updateMember(branchPath, referenceSetMember);
		}

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
}