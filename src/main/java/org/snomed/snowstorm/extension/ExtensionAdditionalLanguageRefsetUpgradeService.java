package org.snomed.snowstorm.extension;

import co.elastic.clients.json.JsonData;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.CodeSystemVersionService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.range;
import static com.google.common.collect.Iterables.partition;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static io.kaicode.elasticvc.helper.QueryHelper.termsQuery;
import static org.snomed.snowstorm.core.data.domain.Description.Fields.DESCRIPTION_ID;
import static org.snomed.snowstorm.core.data.domain.Description.Fields.TYPE_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.*;
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
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private CodeSystemVersionService codeSystemVersionService;

	private final Logger logger = LoggerFactory.getLogger(ExtensionAdditionalLanguageRefsetUpgradeService.class);

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath) || hasPermission('RELEASE_LEAD', #codeSystem.branchPath)")
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
			Map<Long, List<ReferenceSetMember>> languageRefsetMembersToCopy = getReferencedComponents(branchPath, config.getLanguageRefsetIdToCopyFrom(), lastDependantEffectiveTime, currentDependantEffectiveTime);
			logger.info("{} components found with language refset id {} and effective time between {} and {}.", languageRefsetMembersToCopy.keySet().size(),
					config.getLanguageRefsetIdToCopyFrom(), lastDependantEffectiveTime, currentDependantEffectiveTime);
			toSave = addOrUpdateLanguageRefsetComponents(branchPath, config, languageRefsetMembersToCopy);
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
		NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(REFSET_ID, config.getLanguageRefsetIdToCopyFrom()))))
				.withFilter(termQuery(ACTIVE, true))
				.withSourceFilter(new FetchSourceFilter(new String[]{REFERENCED_COMPONENT_ID, ACTIVE, ACCEPTABILITY_ID_FIELD_PATH}, null))
				.withPageable(LARGE_PAGE);

		try (final SearchHitsIterator<ReferenceSetMember> referencedComponents = elasticsearchOperations.searchForStream(searchQueryBuilder.build(), ReferenceSetMember.class)) {
			referencedComponents.forEachRemaining(hit -> result.add(copy(hit.getContent(), config)));
		}
		return result;
	}

	private Map<Long, List<ReferenceSetMember>> getReferencedComponents(String branchPath, String languageRefsetId, Integer lastDependantEffectiveTime, Integer currentDependantEffectiveTime) {
		Objects.requireNonNull(currentDependantEffectiveTime, "Current dependant effective time can't be null");
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Map<Long, List<ReferenceSetMember>> result = new Long2ObjectOpenHashMap<>();
		NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(REFSET_ID, languageRefsetId))))
				.withSourceFilter(new FetchSourceFilter(new String[]{ MEMBER_ID, REFERENCED_COMPONENT_ID, ACTIVE, ACCEPTABILITY_ID_FIELD_PATH, CONCEPT_ID}, null))
				.withPageable(LARGE_PAGE);
		if (lastDependantEffectiveTime != null) {
			// for roll up upgrade every 6 months for example
			searchQueryBuilder.withFilter(range().field(EFFECTIVE_TIME).gt(JsonData.of(lastDependantEffectiveTime)).lte(JsonData.of(currentDependantEffectiveTime)).build()._toQuery());
		} else {
			// for incremental monthly upgrade
			searchQueryBuilder.withFilter(termQuery(EFFECTIVE_TIME, currentDependantEffectiveTime));
		}
		try (final SearchHitsIterator<ReferenceSetMember> referencedComponents = elasticsearchOperations.searchForStream(searchQueryBuilder.build(), ReferenceSetMember.class)) {
			referencedComponents.forEachRemaining(hit ->
					result.computeIfAbsent(Long.valueOf(hit.getContent().getConceptId()), k -> new ArrayList <>()).add(hit.getContent()));
		}
		return result;
	}

	private List<ReferenceSetMember> addOrUpdateLanguageRefsetComponents(String branchPath, AdditionalRefsetExecutionConfig config, Map<Long, List<ReferenceSetMember>> languageRefsetMembersToCopy) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		// Step 1: Get relevant en-gb language refset components to copy and map results by concept id (done in previous step - getReferencedComponents)
		// Collect all relevant description IDs
		Set<Long> descriptionIds = new HashSet<>();
		for (List<ReferenceSetMember> list : languageRefsetMembersToCopy.values()) {
			descriptionIds.addAll(list.stream().map(item -> Long.valueOf(item.getReferencedComponentId())).collect(Collectors.toSet()));
		}

		/* Step 2 */
		// Get all existing language reference set members
		Map<Long, List<ReferenceSetMember>> existingLanguageRefsetMembersToUpdate = getAllExistingLanguageRefsetMembers(config, languageRefsetMembersToCopy, branchCriteria, descriptionIds);

		/* Step 3 */
		// Get all relevant descriptions (requires only SYN and DEF)
		Map<Long, Set<Description>> conceptToDescriptionsMap = getAllRelevantDescriptions(descriptionIds, branchCriteria);

		/* Step 4 */
		// Update the existing language reference set members if needed
		List<ReferenceSetMember> result = new ArrayList<>();
		Set<String> memberIdsToSkipCopy = new HashSet<>();
		updateExistingMembersIfNeeded(config, languageRefsetMembersToCopy, existingLanguageRefsetMembersToUpdate, conceptToDescriptionsMap, result, memberIdsToSkipCopy);

		Set<String> updated = result.stream().map(ReferenceSetMember::getReferencedComponentId).collect(Collectors.toSet());
		logger.info("{} existing components to be updated", updated.size());
		if (!memberIdsToSkipCopy.isEmpty()) {
			logger.info("{} components to skip copy as change exists in extension already.", memberIdsToSkipCopy.size());
		}
		// add new ones
		addNewLanguageRefsetMembers(config, languageRefsetMembersToCopy, existingLanguageRefsetMembersToUpdate, memberIdsToSkipCopy, updated, conceptToDescriptionsMap, result);
		logger.info("{} new components to be added", result.size() - updated.size());
		return result;
	}

	private void addNewLanguageRefsetMembers(AdditionalRefsetExecutionConfig config, Map<Long, List<ReferenceSetMember>> languageRefsetMembersToCopy, Map<Long, List<ReferenceSetMember>> existingLanguageRefsetMembersToUpdate, Set<String> memberIdsToSkipCopy, Set<String> updated, Map<Long, Set<Description>> conceptToDescriptionsMap, List<ReferenceSetMember> result) {
		for (Long conceptId : languageRefsetMembersToCopy.keySet()) {
			List<ReferenceSetMember> toCopyLanguageRefsetMembers = languageRefsetMembersToCopy.get(conceptId);
			Map<String, ReferenceSetMember> languageRefsetMembersToCopyMap = convertMembersListToMap(toCopyLanguageRefsetMembers);

			List<ReferenceSetMember> existingRefsetMembers = existingLanguageRefsetMembersToUpdate.get(conceptId);
			Map<String, ReferenceSetMember> existingLanguageRefsetMembersMap = convertMembersListToMap(existingRefsetMembers);

			for (ReferenceSetMember toCopyMember : toCopyLanguageRefsetMembers) {
				if (!memberIdsToSkipCopy.contains(toCopyMember.getMemberId()) && !updated.contains(toCopyMember.getReferencedComponentId())
					&& isAbleToAddOrUpdate(toCopyMember.getReferencedComponentId(), config.getDefaultModuleId(), languageRefsetMembersToCopyMap, existingLanguageRefsetMembersMap, conceptToDescriptionsMap)) {
					ReferenceSetMember toAdd = new ReferenceSetMember(UUID.randomUUID().toString(), null, toCopyMember.isActive(),
							config.getDefaultModuleId(), config.getDefaultEnglishLanguageRefsetId(), toCopyMember.getReferencedComponentId());
					toAdd.setAdditionalField(ACCEPTABILITY_ID, toCopyMember.getAdditionalField(ACCEPTABILITY_ID));
					result.add(toAdd);
				}
			}
		}
	}

	private void updateExistingMembersIfNeeded(AdditionalRefsetExecutionConfig config, Map<Long, List<ReferenceSetMember>> languageRefsetMembersToCopy, Map<Long, List<ReferenceSetMember>> existingLanguageRefsetMembersToUpdate, Map<Long, Set<Description>> conceptToDescriptionsMap, List<ReferenceSetMember> result, Set<String> memberIdsToSkipCopy) {
		for (Long conceptId : existingLanguageRefsetMembersToUpdate.keySet()) {
			List<ReferenceSetMember> existingRefsetMembers = existingLanguageRefsetMembersToUpdate.get(conceptId);
			if (languageRefsetMembersToCopy.containsKey(conceptId)) {
				Map<String, ReferenceSetMember> languageRefsetMembersToCopyMap = convertMembersListToMap(languageRefsetMembersToCopy.get(conceptId));
				Map<String, ReferenceSetMember> existingLanguageRefsetMembersMap = convertMembersListToMap(existingRefsetMembers);
				for (ReferenceSetMember existingRefsetMember : existingRefsetMembers) {
					if (languageRefsetMembersToCopyMap.containsKey(existingRefsetMember.getReferencedComponentId())
						&& isAbleToAddOrUpdate(existingRefsetMember.getReferencedComponentId(), config.getDefaultModuleId(), languageRefsetMembersToCopyMap, existingLanguageRefsetMembersMap, conceptToDescriptionsMap)) {
						update(existingRefsetMember, languageRefsetMembersToCopyMap, result, memberIdsToSkipCopy);
					}
				}
			}
		}
	}

	@NotNull
	private Map<Long, Set<Description>> getAllRelevantDescriptions(Set<Long> descriptionIds, BranchCriteria branchCriteria) {
		Map<Long, Set<Description>> conceptToDescriptionsMap = new Long2ObjectOpenHashMap<>();
		for (List<Long> batch : partition(descriptionIds, 10_000)) {
			NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(branchCriteria.getEntityBranchCriteria(Description.class))
							.must(termsQuery(TYPE_ID, List.of(Concepts.SYNONYM, Concepts.TEXT_DEFINITION)))))
					.withFilter(termsQuery(DESCRIPTION_ID, batch))
					.withPageable(LARGE_PAGE);

			try (final SearchHitsIterator<Description> searchHitsIterator = elasticsearchOperations.searchForStream(queryBuilder.build(), Description.class)) {
				searchHitsIterator.forEachRemaining(hit -> conceptToDescriptionsMap.computeIfAbsent(Long.valueOf(hit.getContent().getConceptId()), k -> new HashSet<>()).add(hit.getContent()));
			}
		}
		return conceptToDescriptionsMap;
	}

	@NotNull
	private Map<Long, List<ReferenceSetMember>> getAllExistingLanguageRefsetMembers(AdditionalRefsetExecutionConfig config, Map<Long, List<ReferenceSetMember>> languageRefsetMembersToCopy, BranchCriteria branchCriteria, Set<Long> descriptionIds) {
		Map<Long, List<ReferenceSetMember>> existingLanguageRefsetMembersToUpdate = new Long2ObjectOpenHashMap<>();
		for (List<Long> batch : partition(languageRefsetMembersToCopy.keySet(), 10_000)) {
			NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(REFSET_ID, config.getDefaultEnglishLanguageRefsetId()))
							.must(termsQuery(ACCEPTABILITY_ID_FIELD_PATH, List.of(Concepts.PREFERRED, Concepts.ACCEPTABLE)))
							.must(termsQuery(ReferenceSetMember.Fields.CONCEPT_ID, batch)))
					).withPageable(LARGE_PAGE);

			try (final SearchHitsIterator<ReferenceSetMember> searchHitsIterator = elasticsearchOperations.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
				searchHitsIterator.forEachRemaining(hit -> {
					existingLanguageRefsetMembersToUpdate.computeIfAbsent(Long.valueOf(hit.getContent().getConceptId()), k -> new ArrayList <>()).add(hit.getContent());
					descriptionIds.add(Long.valueOf(hit.getContent().getReferencedComponentId()));
				});
			}
		}
		return existingLanguageRefsetMembersToUpdate;
	}

	private boolean isAbleToAddOrUpdate(String referencedComponentId, String defaultModuleId, Map<String, ReferenceSetMember> languageRefsetMembersToCopyMap, Map<String, ReferenceSetMember> existingLanguageRefsetMembersMap, Map<Long, Set<Description>> conceptToDescriptionsMap) {
		if (languageRefsetMembersToCopyMap.containsKey(referencedComponentId)) {
			ReferenceSetMember languageReferenceSetMemberToCopy = languageRefsetMembersToCopyMap.get(referencedComponentId);
			if (languageReferenceSetMemberToCopy.isActive() && Concepts.PREFERRED.equals(languageReferenceSetMemberToCopy.getAdditionalField(ACCEPTABILITY_ID))) {
				Set<Description> descriptions = conceptToDescriptionsMap.get(Long.valueOf(languageReferenceSetMemberToCopy.getConceptId()));
                return descriptions == null || (!isPreferredTermInExtensionFound(referencedComponentId, defaultModuleId, existingLanguageRefsetMembersMap, descriptions, languageReferenceSetMemberToCopy));
			}
			return true;
		}
		return false;
	}

	private boolean isPreferredTermInExtensionFound(String referencedComponentId, String defaultModuleId, Map<String, ReferenceSetMember> existingLanguageRefsetMembersMap, Set<Description> descriptions, ReferenceSetMember languageReferenceSetMemberToCopy) {
		Description descriptionOfReferenceSetMemberToCopy = descriptions.stream().filter(description -> description.getDescriptionId().equals(referencedComponentId)).findFirst().orElse(null);
		if (descriptionOfReferenceSetMemberToCopy != null) {
			for (Description description : descriptions) {
				if (!descriptionOfReferenceSetMemberToCopy.getDescriptionId().equals(description.getDescriptionId())
						&& description.getModuleId().equals(defaultModuleId)
						&& descriptionOfReferenceSetMemberToCopy.getType().equals(description.getType()) /* make sure that we're checking for the same description type */ && (!description.getDescriptionId().equals(languageReferenceSetMemberToCopy.getReferencedComponentId()))) {
						ReferenceSetMember existing = existingLanguageRefsetMembersMap.get(description.getDescriptionId());
						if (existing != null && existing.isActive() && Concepts.PREFERRED.equals(existing.getAdditionalField(ACCEPTABILITY_ID))) {
							return true;
						}

				}
			}
		}
		return false;
	}

	private Map<String, ReferenceSetMember> convertMembersListToMap(List<ReferenceSetMember> members) {
		return members == null ? new HashMap<>() : members.stream().collect(Collectors.toMap(ReferenceSetMember::getReferencedComponentId, Function.identity()));
	}

	private ReferenceSetMember copy(ReferenceSetMember enGbMember, AdditionalRefsetExecutionConfig config) {
		ReferenceSetMember extensionMember = new ReferenceSetMember(UUID.randomUUID().toString(), null, true,
				config.getDefaultModuleId(), config.getDefaultEnglishLanguageRefsetId(), enGbMember.getReferencedComponentId());
		extensionMember.setAdditionalField(ACCEPTABILITY_ID, enGbMember.getAdditionalField(ACCEPTABILITY_ID));
		extensionMember.markChanged();
		return extensionMember;
	}

	private void update(ReferenceSetMember member, Map<String, ReferenceSetMember> existingComponents, List<ReferenceSetMember> result, Set<String> memberIdsToSkipCopy	) {
		if (existingComponents.containsKey(member.getReferencedComponentId())) {
			// only updates when active or acceptability is changed
			ReferenceSetMember existing = existingComponents.get(member.getReferencedComponentId());
			if (existing.isActive() != member.isActive() || !existing.getAdditionalField(ACCEPTABILITY_ID).equals(member.getAdditionalField(ACCEPTABILITY_ID))) {
				member.setActive(existing.isActive());
				member.setEffectiveTimeI(null);
				member.setAdditionalField(ACCEPTABILITY_ID, existing.getAdditionalField(ACCEPTABILITY_ID));
				result.add(member);
			} else {
				memberIdsToSkipCopy.add(existing.getMemberId());
				logger.debug("No need to copy change of language refset member {} to extension because it has the latest change already {}", existing.getMemberId(), member.getMemberId());
			}
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
