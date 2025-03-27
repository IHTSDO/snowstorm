package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.stereotype.Service;
import io.kaicode.elasticvc.api.ComponentService;

import java.util.*;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;

@Service
public class ModuleDependencyService extends ComponentService {
	private static final Set<String> INTERNATIONAL_MODULES = Set.of(Concepts.CORE_MODULE, Concepts.MODEL_MODULE, Concepts.ICD10_MODULE, Concepts.ICD11_MODULE);

	private final BranchService branchService;
	private final CodeSystemService codeSystemService;
	private final ReferenceSetMemberService referenceSetMemberService;
	private final AuthoringStatsService authoringStatsService;
	private final ElasticsearchOperations elasticsearchOperations;

	public ModuleDependencyService(BranchService branchService, CodeSystemService codeSystemService, @Lazy ReferenceSetMemberService referenceSetMemberService, AuthoringStatsService authoringStatsService, ElasticsearchOperations elasticsearchOperations) {
		this.branchService = branchService;
		this.codeSystemService = codeSystemService;
		this.referenceSetMemberService = referenceSetMemberService;
		this.authoringStatsService = authoringStatsService;
		this.elasticsearchOperations = elasticsearchOperations;
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

		Branch branch = branchService.findLatest(branchPath);
		if (branch == null || branch.getMetadata() == null || branch.getMetadata().size() == 0) {
			return false;
		}

		Optional<CodeSystem> codeSystem = codeSystemService.findByBranchPath(branchPath);
		if (codeSystem.isEmpty()) {
			return false;
		}
		branchPath = codeSystem.get().getBranchPath();

		Set<String> modules = getModules(branch);
		if (modules.isEmpty()) {
			return false;
		}

		List<ReferenceSetMember> referenceSetMembers = getMDRSEntries(branch, modules);
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

	private Set<String> getModules(Branch branch) {
		Set<String> modules = new HashSet<>();

		// Collect modules from content
		modules.addAll(authoringStatsService.getComponentCountsPerModule(branch.getPath()).values().stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.collect(Collectors.toSet()));

		// Collect modules from branch metadata
		Metadata metadata = branch.getMetadata();
		boolean isExtension = metadata.getString(BranchMetadataKeys.DEPENDENCY_PACKAGE) != null;
		if (!isExtension) {
			modules.addAll(INTERNATIONAL_MODULES);
		} else if (metadata.containsKey(BranchMetadataKeys.EXPECTED_EXTENSION_MODULES)) {
			modules.removeAll(INTERNATIONAL_MODULES);
			modules.addAll(metadata.getList(BranchMetadataKeys.EXPECTED_EXTENSION_MODULES));
		}

		return modules;
	}

	private List<ReferenceSetMember> getMDRSEntries(Branch branch, Set<String> modules) {
		List<ReferenceSetMember> referenceSetMembers = new ArrayList<>();
		try (SearchHitsIterator<ReferenceSetMember> hits = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
				.withQuery(bool(bq -> {
					bq
							.must(termQuery(SnomedComponent.Fields.PATH, branch.getPath()))
							.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
							.mustNot(existsQuery(SnomedComponent.Fields.END))
							.must(termsQuery(SnomedComponent.Fields.MODULE_ID, modules))
							.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.MODULE_DEPENDENCY_REFERENCE_SET));

					return bq;
				}))
				.withPageable(LARGE_PAGE)
				.build(), ReferenceSetMember.class)) {
			hits.forEachRemaining(hit -> referenceSetMembers.add(hit.getContent()));
		}

		return referenceSetMembers;
	}
}