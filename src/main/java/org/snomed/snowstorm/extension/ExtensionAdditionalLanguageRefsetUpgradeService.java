package org.snomed.snowstorm.extension;

import co.elastic.clients.json.JsonData;
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
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.CodeSystemVersionService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterables.partition;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
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
				.withFields(REFERENCED_COMPONENT_ID, ACTIVE, ACCEPTABILITY_ID_FIELD_PATH)
				.withPageable(LARGE_PAGE);

		try (final SearchHitsIterator<ReferenceSetMember> referencedComponents = elasticsearchTemplate.searchForStream(searchQueryBuilder.build(), ReferenceSetMember.class)) {
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
				.withFields(REFERENCED_COMPONENT_ID, ACTIVE, ACCEPTABILITY_ID_FIELD_PATH, CONCEPT_ID)
				.withPageable(LARGE_PAGE);
		if (lastDependantEffectiveTime != null) {
			// for roll up upgrade every 6 months for example
			searchQueryBuilder.withFilter(range().field(EFFECTIVE_TIME).gt(JsonData.of(lastDependantEffectiveTime)).lte(JsonData.of(currentDependantEffectiveTime)).build()._toQuery());
		} else {
			// for incremental monthly upgrade
			searchQueryBuilder.withFilter(termQuery(EFFECTIVE_TIME, currentDependantEffectiveTime));
		}
		try (final SearchHitsIterator<ReferenceSetMember> referencedComponents = elasticsearchTemplate.searchForStream(searchQueryBuilder.build(), ReferenceSetMember.class)) {
			referencedComponents.forEachRemaining(hit ->
					result.computeIfAbsent(Long.valueOf(hit.getContent().getConceptId()), k -> new ArrayList <>()).add(hit.getContent()));
		}
		return result;
	}

	private List<ReferenceSetMember> addOrUpdateLanguageRefsetComponents(String branchPath, AdditionalRefsetExecutionConfig config, Map<Long, List<ReferenceSetMember>> languageRefsetMembersToCopy) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		List<ReferenceSetMember> result = new ArrayList<>();

		// Step 1: Get relevant en-gb language refset components to copy and map results by concept id (done in previous step - getReferencedComponents)
		// Collect all relevant description IDs
		Set<Long> descriptionIds = new HashSet<>();
		for (List<ReferenceSetMember> list : languageRefsetMembersToCopy.values()) {
			descriptionIds.addAll(list.stream().map(item -> Long.valueOf(item.getReferencedComponentId())).collect(Collectors.toSet()));
		}

		/* Step 2 */
		// Get all existing language reference set members
		Map<Long, List<ReferenceSetMember>> existingLanguageRefsetMembersToUpdate = new Long2ObjectOpenHashMap<>();
		for (List<Long> batch : partition(languageRefsetMembersToCopy.keySet(), 10_000)) {
			NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(REFSET_ID, config.getDefaultEnglishLanguageRefsetId()))
							.must(termsQuery(ACCEPTABILITY_ID_FIELD_PATH, List.of(Concepts.PREFERRED, Concepts.ACCEPTABLE)))
							.must(termsQuery(ReferenceSetMember.Fields.CONCEPT_ID, batch)))
					).withFields(MEMBER_ID, EFFECTIVE_TIME, ACTIVE, MODULE_ID, REFSET_ID, REFERENCED_COMPONENT_ID, ACCEPTABILITY_ID_FIELD_PATH, CONCEPT_ID)
					.withPageable(LARGE_PAGE);

			try (final SearchHitsIterator<ReferenceSetMember> searchHitsIterator = elasticsearchTemplate.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
				searchHitsIterator.forEachRemaining(hit -> {
					existingLanguageRefsetMembersToUpdate.computeIfAbsent(Long.valueOf(hit.getContent().getConceptId()), k -> new ArrayList <>()).add(hit.getContent());
					descriptionIds.add(Long.valueOf(hit.getContent().getReferencedComponentId()));
				});
			}
		}

		/* Step 3 */
		// Get all relevant descriptions (requires only SYN and DEF)
		Map<Long, Set<Description>> conceptToDescriptionsMap = new Long2ObjectOpenHashMap<>();
		for (List<Long> batch : partition(descriptionIds, 10_000)) {
			NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(branchCriteria.getEntityBranchCriteria(Description.class))
							.must(termsQuery(TYPE_ID, List.of(Concepts.SYNONYM, Concepts.TEXT_DEFINITION)))))
					.withFilter(termsQuery(DESCRIPTION_ID, batch))
					.withPageable(LARGE_PAGE);

			try (final SearchHitsIterator<Description> searchHitsIterator = elasticsearchTemplate.searchForStream(queryBuilder.build(), Description.class)) {
				searchHitsIterator.forEachRemaining(hit -> conceptToDescriptionsMap.computeIfAbsent(Long.valueOf(hit.getContent().getConceptId()), k -> new HashSet<>()).add(hit.getContent()));
			}
		}

		/* Step 4 */
		// Update the existing language reference set members if needed
		for (Long conceptId : existingLanguageRefsetMembersToUpdate.keySet()) {
			List<ReferenceSetMember> existingRefsetMembers = existingLanguageRefsetMembersToUpdate.get(conceptId);
			if (languageRefsetMembersToCopy.containsKey(conceptId)) {
				Map<String, ReferenceSetMember> languageRefsetMembersToCopyMap = convertMembersListToMap(languageRefsetMembersToCopy.get(conceptId));
				Map<String, ReferenceSetMember> existingLanguageRefsetMembersMap = convertMembersListToMap(existingRefsetMembers);
				for (ReferenceSetMember existingRefsetMember : existingRefsetMembers) {
					if (languageRefsetMembersToCopyMap.containsKey(existingRefsetMember.getReferencedComponentId())
						&& isAbleToAddOrUpdate(existingRefsetMember.getReferencedComponentId(), config.getDefaultModuleId(), languageRefsetMembersToCopyMap, existingLanguageRefsetMembersMap, conceptToDescriptionsMap)) {
						update(existingRefsetMember, languageRefsetMembersToCopyMap, result);
					}
				}
			}
		}

		Set<String> updated = result.stream().map(ReferenceSetMember::getReferencedComponentId).collect(Collectors.toSet());
		logger.info("{} components to be updated", updated.size());
		// add new ones
		for (Long conceptId : languageRefsetMembersToCopy.keySet()) {
			List<ReferenceSetMember> toCopyLanguageRefsetMembers = languageRefsetMembersToCopy.get(conceptId);
			Map<String, ReferenceSetMember> languageRefsetMembersToCopyMap = convertMembersListToMap(toCopyLanguageRefsetMembers);

			List<ReferenceSetMember> existingRefsetMembers = existingLanguageRefsetMembersToUpdate.get(conceptId);
			Map<String, ReferenceSetMember> existingLanguageRefsetMembersMap = convertMembersListToMap(existingRefsetMembers);

			for (ReferenceSetMember toCopyMember : toCopyLanguageRefsetMembers) {
				if (!updated.contains(toCopyMember.getReferencedComponentId())
					&& isAbleToAddOrUpdate(toCopyMember.getReferencedComponentId(), config.getDefaultModuleId(), languageRefsetMembersToCopyMap, existingLanguageRefsetMembersMap, conceptToDescriptionsMap)) {
					ReferenceSetMember toAdd = new ReferenceSetMember(UUID.randomUUID().toString(), null, toCopyMember.isActive(),
							config.getDefaultModuleId(), config.getDefaultEnglishLanguageRefsetId(), toCopyMember.getReferencedComponentId());
					toAdd.setAdditionalField(ACCEPTABILITY_ID, toCopyMember.getAdditionalField(ACCEPTABILITY_ID));
					result.add(toAdd);
				}
			}
		}
		logger.info("{} new components to be added", result.size() - updated.size());
		return result;
	}

	private boolean isAbleToAddOrUpdate(String referencedComponentId, String defaultModuleId, Map<String, ReferenceSetMember> languageRefsetMembersToCopyMap, Map<String, ReferenceSetMember> existingLanguageRefsetMembersMap, Map<Long, Set<Description>> conceptToDescriptionsMap) {
		if (languageRefsetMembersToCopyMap.containsKey(referencedComponentId)) {
			ReferenceSetMember languageReferenceSetMemberToCopy = languageRefsetMembersToCopyMap.get(referencedComponentId);
			if (languageReferenceSetMemberToCopy.isActive() && Concepts.PREFERRED.equals(languageReferenceSetMemberToCopy.getAdditionalField(ACCEPTABILITY_ID))) {
				Set<Description> descriptions = conceptToDescriptionsMap.get(Long.valueOf(languageReferenceSetMemberToCopy.getConceptId()));
				if (descriptions != null) {
					Description descriptionOfReferenceSetMemberToCopy = descriptions.stream().filter(description -> description.getDescriptionId().equals(referencedComponentId)).findFirst().orElse(null);
					if (descriptionOfReferenceSetMemberToCopy != null) {
						for (Description description : descriptions) {
							if (!descriptionOfReferenceSetMemberToCopy.getDescriptionId().equals(description.getDescriptionId())
									&& description.getModuleId().equals(defaultModuleId)
									&& descriptionOfReferenceSetMemberToCopy.getType().equals(description.getType()) /* make sure that we're checking for the same description type */) {
								// Check if the extension descriptions already have any PREFERRED term.
								// If yes, skip the EN-GB Reference Set add/update.
								if (!description.getDescriptionId().equals(languageReferenceSetMemberToCopy.getReferencedComponentId())) {
									ReferenceSetMember existing = existingLanguageRefsetMembersMap.get(description.getDescriptionId());
									if (existing != null && existing.isActive() && Concepts.PREFERRED.equals(existing.getAdditionalField(ACCEPTABILITY_ID))) {
										return false;
									}
								}
							}
						}
					}
				}
			}
			return true;
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

	private void update(ReferenceSetMember member, Map<String, ReferenceSetMember> existingComponents, List<ReferenceSetMember> result) {
		if (existingComponents.containsKey(member.getReferencedComponentId())) {
			// update
			ReferenceSetMember existing = existingComponents.get(member.getReferencedComponentId());
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
