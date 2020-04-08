package org.snomed.snowstorm.extension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.repositories.ReferenceSetMemberRepository;
import org.snomed.snowstorm.core.data.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.MEMBER_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.REFSET_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH;
import static org.snomed.snowstorm.core.data.domain.SnomedComponent.Fields.ACTIVE;
import static org.snomed.snowstorm.core.data.domain.SnomedComponent.Fields.EFFECTIVE_TIME;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.*;

import static com.google.common.collect.Iterables.partition;

@Service
public class ExtensionAdditionalLanguageRefsetUpgradeService extends ComponentService {
	@Autowired
	private CodeSystemService codeSystemService;

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
	private ReferenceSetMemberRepository memberRepository;

	private Gson gson = new GsonBuilder().create();

	private static final String GB_ENGLISH_LANGUAGE_REFSET_ID = "900000000000508004";

	private Logger logger = LoggerFactory.getLogger(ExtensionAdditionalLanguageRefsetUpgradeService.class);


	public void generateAdditionalLanguageRefsetDelta(CodeSystem codeSystem, String branchPath, Boolean firstTime) {
		logger.info("Start updating additional language refset on branch {} for {}.", branchPath, codeSystem);
		AdditionalRefsetExecutionConfig config = createExecutionConfig(codeSystem, firstTime);
		String lockMsg = String.format("Add or update additional language refset on branch %s ", branchPath);
		try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata(lockMsg))) {
			performUpdate(config, branchPath, commit);
			commit.markSuccessful();
		}
		logger.info("Completed updating additional language refset on branch {}.", branchPath);
	}

	private void performUpdate(AdditionalRefsetExecutionConfig config, String branchPath, Commit commit) {
		Integer effectiveTime = null;
		if (!config.isFirstTimeUpgrade()) {
			effectiveTime = config.getCodeSystem().getDependantVersionEffectiveTime();
		}
		Map<Long, ReferenceSetMember> enGbComponents = getReferencedComponents(branchPath, GB_ENGLISH_LANGUAGE_REFSET_ID, effectiveTime);
		logger.info("{} components found with en gb language refset id.", enGbComponents.keySet().size());
		List<ReferenceSetMember> toSave = addOrUpdateLanguageRefsetComponents(branchPath, config, enGbComponents);
		toSave.stream().forEach(ReferenceSetMember :: updateEffectiveTime);
		toSave.stream().forEach(ReferenceSetMember :: markChanged);
		doSaveBatchComponents(toSave, commit, MEMBER_ID, memberRepository);
	}

	private Map<Long, ReferenceSetMember> getReferencedComponents(String branchPath, String languageRefsetId, Integer effectiveTime) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Map<Long, ReferenceSetMember> result = new Long2ObjectOpenHashMap<>();
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(REFSET_ID, languageRefsetId))
				);

		if (effectiveTime != null) {
			searchQueryBuilder.withFilter(termQuery(EFFECTIVE_TIME, effectiveTime));
		} else {
			// first time with active only
			searchQueryBuilder.withFilter(termQuery(ACTIVE, true));
		}
		searchQueryBuilder.withFields(REFERENCED_COMPONENT_ID, ACTIVE, ACCEPTABILITY_ID_FIELD_PATH)
				.withPageable(LARGE_PAGE);

		try (final CloseableIterator<ReferenceSetMember> referencedComponents = elasticsearchTemplate.stream(searchQueryBuilder.build(), ReferenceSetMember.class)) {
			referencedComponents.forEachRemaining(referenceSetMember ->
					result.put(new Long(referenceSetMember.getReferencedComponentId()), referenceSetMember));
		}
		return result;
	}

	private List<ReferenceSetMember> addOrUpdateLanguageRefsetComponents(String branchPath, AdditionalRefsetExecutionConfig config, Map<Long, ReferenceSetMember> existing) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		List<ReferenceSetMember> result = new ArrayList<>();
		// batch here as the max allowed in terms query is 65536
		for (List<Long> batch : partition(existing.keySet(), CLAUSE_LIMIT)) {
			NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(REFSET_ID, config.getDefaultEnglishLanguageRefsetId()))
					).withFilter(termsQuery(REFERENCED_COMPONENT_ID, batch))
					.withPageable(ConceptService.LARGE_PAGE);

			try (final CloseableIterator<ReferenceSetMember> componentsToUpdate = elasticsearchTemplate.stream(queryBuilder.build(), ReferenceSetMember.class)) {
				componentsToUpdate.forEachRemaining(referenceSetMember -> update(referenceSetMember, existing, config, result));
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

	private void update(ReferenceSetMember member, Map<Long, ReferenceSetMember> existingComponents, AdditionalRefsetExecutionConfig config, List<ReferenceSetMember> result) {
		if (existingComponents.containsKey(member.getReferencedComponentId())) {
			// update
			ReferenceSetMember existing = existingComponents.get(new Long(member.getReferencedComponentId()));
			member.setActive(existing.isActive());
			member.setChanged(true);
			member.setAdditionalField(ACCEPTABILITY_ID, existing.getAdditionalField(ACCEPTABILITY_ID));
			result.add(member);
		}
	}

	private AdditionalRefsetExecutionConfig createExecutionConfig(CodeSystem codeSystem, Boolean firstTime) {
		AdditionalRefsetExecutionConfig config = new AdditionalRefsetExecutionConfig(codeSystem, firstTime);
		Branch branch = branchService.findLatest(codeSystem.getBranchPath());
		Map<String, Object> expandedMetadata = branchMetadataHelper.expandObjectValues(branch.getMetadata());
		Object jsonObject = expandedMetadata.get(REQUIRED_LANGUAGE_REFSETS);
		LanguageRefsetMetadataConfig[] configs = gson.fromJson(jsonObject.toString(), LanguageRefsetMetadataConfig[].class);
		String defaultEnglishLanguageRefsetId = null;
		for (LanguageRefsetMetadataConfig metadataConfig : configs) {
			if (metadataConfig.usedByDefault && metadataConfig.getEnglishLanguageRestId() != null)
			{
				defaultEnglishLanguageRefsetId = metadataConfig.getEnglishLanguageRestId();
				break;
			}
		}
		if (defaultEnglishLanguageRefsetId == null) {
			throw new IllegalStateException("Missing default language refset id for en language in the metadata.");
		}
		String defaultModuleId = branch.getMetadata().get(DEFAULT_MODULE_ID);
		if (defaultModuleId == null) {
			throw new IllegalStateException("Missing default module id config in the metadata.");
		}
		config.setDefaultEnglishLanguageRefsetId(defaultEnglishLanguageRefsetId);
		config.setDefaultModuleId(defaultModuleId);
		return config;
	}

	 private static class AdditionalRefsetExecutionConfig {
		private CodeSystem codeSystem;
		private String defaultModuleId;
		private String defaultEnglishLanguageRefsetId;
		private boolean firstTimeUpgrade;

		public AdditionalRefsetExecutionConfig(CodeSystem codeSystem, Boolean firstTime) {
			this.codeSystem = codeSystem;
			firstTimeUpgrade = firstTime == null ? false : firstTime;
		}

		public void setDefaultEnglishLanguageRefsetId(String defaultEnglishLanguageRefsetId) {
			this.defaultEnglishLanguageRefsetId = defaultEnglishLanguageRefsetId;
		}

		public String getDefaultEnglishLanguageRefsetId() {
			return defaultEnglishLanguageRefsetId;
		}

		public CodeSystem getCodeSystem() {
			return codeSystem;
		}

		public String getDefaultModuleId() {
			return defaultModuleId;
		}

		public void setDefaultModuleId(String defaultModuleId) {
			this.defaultModuleId = defaultModuleId;
		}

		public boolean isFirstTimeUpgrade() { return this.firstTimeUpgrade; }
	}

	private static class LanguageRefsetMetadataConfig {

		@SerializedName(value = "usedByDefault", alternate = "default")
		private boolean usedByDefault;

		@SerializedName(value = "englishLanguageRefsetId", alternate = "en")
		private String englishLanguageRestId;

		private boolean readOnly;

		private String dialectName;

		public boolean isUsedByDefault() {
			return usedByDefault;
		}

		public void setUsedByDefault(boolean usedByDefault) {
			this.usedByDefault = usedByDefault;
		}

		public String getEnglishLanguageRestId() {
			return englishLanguageRestId;
		}

		public void setEnglishLanguageRefsetId(String englishLanguageRestId) {
			this.englishLanguageRestId = englishLanguageRestId;
		}
		public boolean isReadOnly() {
			return readOnly;
		}

		public void setReadOnly(boolean readOnly) {
			this.readOnly = readOnly;
		}

		public String getDialectName() {
			return dialectName;
		}

		public void setDialectName(String dialectName) {
			this.dialectName = dialectName;
		}
	}
}
