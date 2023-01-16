package org.snomed.snowstorm.extension;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.CodeSystemVersionService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterables.partition;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.REFSET_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH;
import static org.snomed.snowstorm.core.data.domain.SnomedComponent.Fields.ACTIVE;
import static org.snomed.snowstorm.core.data.domain.SnomedComponent.Fields.EFFECTIVE_TIME;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.DEFAULT_MODULE_ID;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.REQUIRED_LANGUAGE_REFSETS;

@Service
public class ExtensionAdditionalLanguageRefsetUpgradeService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private CodeSystemVersionService codeSystemVersionService;

	private final Logger logger = LoggerFactory.getLogger(ExtensionAdditionalLanguageRefsetUpgradeService.class);

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public void generateAdditionalLanguageRefsetDelta(CodeSystem codeSystem, String branchPath, String languageRefsetId, Boolean completeCopy) {
		logger.info("Start updating additional language refset on branch {} for {}.", branchPath, codeSystem);
		AdditionalRefsetExecutionConfig config = createExecutionConfig(codeSystem, completeCopy);
		config.setLanguageRefsetIdToCopyFrom(languageRefsetId);
		performUpdate(config, branchPath);
		logger.info("Completed updating additional language refset on branch {}.", branchPath);
	}

	private void performUpdate(AdditionalRefsetExecutionConfig config, String branchPath) {
		List<ReferenceSetMember> toSave;
		if (config.isCompleteCopy()) {
			// copy everything and change module id and refset id
			toSave = copyAll(branchPath, config);
			logger.info("{} active components found with language refset id {}.", toSave.size(), config.getLanguageRefsetIdToCopyFrom());
		} else {
			// add/update components from dependent release delta
			Integer currentDependantEffectiveTime = config.getCodeSystem().getDependantVersionEffectiveTime();
			if (currentDependantEffectiveTime == null) {
				throw new NotFoundException("No dependent version found in CodeSystem " +  config.getCodeSystem().getShortName());
			}
			Integer lastDependantEffectiveTime = null;
			if (config.getCodeSystem().getLatestVersion() != null) {
				// Currently dependant release is not populated when versioning but during start up
				codeSystemVersionService.populateDependantVersion(config.getCodeSystem().getLatestVersion());
				lastDependantEffectiveTime = config.getCodeSystem().getLatestVersion().getDependantVersionEffectiveTime();
			}
			if (lastDependantEffectiveTime == null) {
				logger.info("No dependent version found in the latest version {} for CodeSystem {}", config.getCodeSystem().getLatestVersion(), config.getCodeSystem().getShortName());
			}
			Map<Long, ReferenceSetMember> enGbComponents = getReferencedComponents(branchPath, config.getLanguageRefsetIdToCopyFrom(), lastDependantEffectiveTime, currentDependantEffectiveTime);
			logger.info("{} components found with language refset id {} and effective time between {} and {}.", enGbComponents.keySet().size(),
					config.getLanguageRefsetIdToCopyFrom(), lastDependantEffectiveTime, currentDependantEffectiveTime);
			toSave = addOrUpdateLanguageRefsetComponents(branchPath, config, enGbComponents);
			toSave.forEach(ReferenceSetMember::markChanged);
		}
		if (!toSave.isEmpty()) {
			String lockMsg = String.format("Add or update additional language refset on branch %s ", branchPath);
			try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata(lockMsg))) {
				referenceSetMemberService.doSaveBatchMembers(toSave, commit);
				commit.markSuccessful();
				logger.info("{} components saved.", toSave.size());
			}
		}
	}

	private List<ReferenceSetMember> copyAll(String branchPath, AdditionalRefsetExecutionConfig config) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		List<ReferenceSetMember> result = new ArrayList<>();
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(REFSET_ID, config.getLanguageRefsetIdToCopyFrom())))
				.withFilter(termQuery(ACTIVE, true))
				.withFields(REFERENCED_COMPONENT_ID, ACTIVE, ACCEPTABILITY_ID_FIELD_PATH)
				.withPageable(LARGE_PAGE);

		try (final SearchHitsIterator<ReferenceSetMember> referencedComponents = elasticsearchTemplate.searchForStream(searchQueryBuilder.build(), ReferenceSetMember.class)) {
			referencedComponents.forEachRemaining(hit -> result.add(copy(hit.getContent(), config)));
		}
		return result;
	}

	private Map<Long, ReferenceSetMember> getReferencedComponents(String branchPath, String languageRefsetId, Integer lastDependantEffectiveTime, Integer currentDependantEffectiveTime) {
		Objects.requireNonNull(currentDependantEffectiveTime, "Current dependant effective time can't be null");
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Map<Long, ReferenceSetMember> result = new Long2ObjectOpenHashMap<>();
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(REFSET_ID, languageRefsetId)))
				.withFields(REFERENCED_COMPONENT_ID, ACTIVE, ACCEPTABILITY_ID_FIELD_PATH)
				.withPageable(LARGE_PAGE);
		if (lastDependantEffectiveTime != null) {
			// for roll up upgrade every 6 months for example
			searchQueryBuilder.withFilter(rangeQuery(EFFECTIVE_TIME).gt(lastDependantEffectiveTime).lte(currentDependantEffectiveTime));
		} else {
			// for incremental monthly upgrade
			searchQueryBuilder.withFilter(termQuery(EFFECTIVE_TIME, currentDependantEffectiveTime));
		}
		try (final SearchHitsIterator<ReferenceSetMember> referencedComponents = elasticsearchTemplate.searchForStream(searchQueryBuilder.build(), ReferenceSetMember.class)) {
			referencedComponents.forEachRemaining(hit ->
					result.put(Long.valueOf(hit.getContent().getReferencedComponentId()), hit.getContent()));
		}
		return result;
	}

	private List<ReferenceSetMember> addOrUpdateLanguageRefsetComponents(String branchPath, AdditionalRefsetExecutionConfig config, Map<Long, ReferenceSetMember> existing) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		List<ReferenceSetMember> result = new ArrayList<>();
		// batch here as the max allowed in terms query is 65536
		for (List<Long> batch : partition(existing.keySet(), 10_000)) {
			NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(REFSET_ID, config.getDefaultEnglishLanguageRefsetId()))
					).withFilter(termsQuery(REFERENCED_COMPONENT_ID, batch))
					.withPageable(LARGE_PAGE);

			try (final SearchHitsIterator<ReferenceSetMember> componentsToUpdate = elasticsearchTemplate.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
				componentsToUpdate.forEachRemaining(hit -> update(hit.getContent(), existing, result));
			}
		}

		Set<String> updated = result.stream().map(ReferenceSetMember::getReferencedComponentId).collect(Collectors.toSet());
		// add new ones
		for (Long referencedComponentId : existing.keySet()) {
			if (!updated.contains(referencedComponentId.toString())) {
				ReferenceSetMember toAdd = new ReferenceSetMember(UUID.randomUUID().toString(), null, existing.get(referencedComponentId).isActive(),
						config.getDefaultModuleId(), config.getDefaultEnglishLanguageRefsetId(), existing.get(referencedComponentId).getReferencedComponentId());
				toAdd.setAdditionalField(ACCEPTABILITY_ID, existing.get(referencedComponentId).getAdditionalField(ACCEPTABILITY_ID));
				result.add(toAdd);
			}
		}
		return result;
	}

	private ReferenceSetMember copy(ReferenceSetMember enGbMember, AdditionalRefsetExecutionConfig config) {
		ReferenceSetMember extensionMember = new ReferenceSetMember(UUID.randomUUID().toString(), null, true,
				config.getDefaultModuleId(), config.getDefaultEnglishLanguageRefsetId(), enGbMember.getReferencedComponentId());
		extensionMember.setAdditionalField(ACCEPTABILITY_ID, enGbMember.getAdditionalField(ACCEPTABILITY_ID));
		extensionMember.markChanged();
		return extensionMember;
	}

	private void update(ReferenceSetMember member, Map<Long, ReferenceSetMember> existingComponents, List<ReferenceSetMember> result) {
		if (existingComponents.containsKey(Long.valueOf(member.getReferencedComponentId()))) {
			// update
			ReferenceSetMember existing = existingComponents.get(Long.valueOf(member.getReferencedComponentId()));
			member.setActive(existing.isActive());
			member.setEffectiveTimeI(null);
			member.setAdditionalField(ACCEPTABILITY_ID, existing.getAdditionalField(ACCEPTABILITY_ID));
			result.add(member);
		}
	}

	private AdditionalRefsetExecutionConfig createExecutionConfig(CodeSystem codeSystem, Boolean completeCopy) {
		AdditionalRefsetExecutionConfig config = new AdditionalRefsetExecutionConfig(codeSystem, completeCopy);
		Branch branch = branchService.findLatest(codeSystem.getBranchPath());
		final Metadata metadata = branch.getMetadata();
		String defaultEnglishLanguageRefsetId = null;
		if (metadata.containsKey(REQUIRED_LANGUAGE_REFSETS)) {
			@SuppressWarnings("unchecked")
			List<Map<String, String>> requiredLangRefsets = (List<Map<String, String>>) metadata.getAsMap().get(REQUIRED_LANGUAGE_REFSETS);
			for (Map<String, String> requiredLangRefset : requiredLangRefsets) {
				if (Boolean.parseBoolean(requiredLangRefset.get("default")) && requiredLangRefset.get("en") != null) {
					defaultEnglishLanguageRefsetId = requiredLangRefset.get("en");
					break;
				}
			}
		}
		if (defaultEnglishLanguageRefsetId == null) {
			throw new IllegalStateException("Missing default language refset id for en language in the metadata.");
		}
		String defaultModuleId = metadata.getString(DEFAULT_MODULE_ID);
		if (defaultModuleId == null) {
			throw new IllegalStateException("Missing default module id config in the metadata.");
		}
		config.setDefaultEnglishLanguageRefsetId(defaultEnglishLanguageRefsetId);
		config.setDefaultModuleId(defaultModuleId);
		return config;
	}

	 private static class AdditionalRefsetExecutionConfig {
		private final CodeSystem codeSystem;
		private String defaultModuleId;
		private String defaultEnglishLanguageRefsetId;
		private final boolean completeCopy;
		private String languageRefsetIdToCopyFrom;

		AdditionalRefsetExecutionConfig(CodeSystem codeSystem, Boolean completeCopy) {
			this.codeSystem = codeSystem;
			this.completeCopy = completeCopy != null && completeCopy;
		}

		void setDefaultEnglishLanguageRefsetId(String defaultEnglishLanguageRefsetId) {
			this.defaultEnglishLanguageRefsetId = defaultEnglishLanguageRefsetId;
		}

		String getDefaultEnglishLanguageRefsetId() {
			return defaultEnglishLanguageRefsetId;
		}

		CodeSystem getCodeSystem() {
			return codeSystem;
		}

		String getDefaultModuleId() {
			return defaultModuleId;
		}

		void setDefaultModuleId(String defaultModuleId) {
			this.defaultModuleId = defaultModuleId;
		}

		boolean isCompleteCopy() { return this.completeCopy; }

		void setLanguageRefsetIdToCopyFrom(String languageRefsetIdToCopyFrom) {
			this.languageRefsetIdToCopyFrom = languageRefsetIdToCopyFrom;
		 }

		 String getLanguageRefsetIdToCopyFrom() {
			return this.languageRefsetIdToCopyFrom;
		 }
	 }

}
