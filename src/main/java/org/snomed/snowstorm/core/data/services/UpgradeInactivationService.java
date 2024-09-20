package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.CLAUSE_LIMIT;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.*;

@Service
public class UpgradeInactivationService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void findAndUpdateDescriptionsInactivation(CodeSystem codeSystem) {
		if (codeSystem == null) {
			throw new IllegalArgumentException("CodeSystem must not be null");
		}
		String branchPath = codeSystem.getBranchPath();
		logger.info("Start auto description inactivation for inactive concepts for code system {} on branch {}", codeSystem.getShortName(), branchPath);
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		// find inactive concept ids
		NativeQuery inactiveConceptQuery = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, false))))
				.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
				.withPageable(ComponentService.LARGE_PAGE)
				.build();
		List<Long> inactiveConceptIds = new LongArrayList();
		try (SearchHitsIterator<Concept> conceptResults = elasticsearchOperations.searchForStream(inactiveConceptQuery, Concept.class)) {
			conceptResults.forEachRemaining(hit -> inactiveConceptIds.add((hit.getContent().getConceptIdAsLong())));
		}

		List<ReferenceSetMember> membersToSave = new ArrayList<>();
		List<String> expectedExtensionModules = getExpectedExtensionModules(branchPath);
		// find descriptions with inactivation indicators on the extension branch only
		BranchCriteria changesOnBranchOnly = versionControlHelper.getChangesOnBranchCriteria(codeSystem.getBranchPath());
		for (List<Long> batch : Iterables.partition(inactiveConceptIds, CLAUSE_LIMIT)) {
			BoolQuery.Builder queryDescriptionsWithInactivationIndicators = bool()
					.must(changesOnBranchOnly.getEntityBranchCriteria(ReferenceSetMember.class))
					.must(termQuery(REFSET_ID, Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET))
					.must(termQuery(ADDITIONAL_FIELDS_PREFIX + "valueId", Concepts.CONCEPT_NON_CURRENT))
					.must(termsQuery(CONCEPT_ID, batch))
					.must(termQuery(ACTIVE, true));

			if (expectedExtensionModules != null) {
				queryDescriptionsWithInactivationIndicators.must(termsQuery(MODULE_ID, expectedExtensionModules));
			}

			NativeQuery descriptionsWithInactivationIndicators = new NativeQueryBuilder()
					.withQuery(queryDescriptionsWithInactivationIndicators.build()._toQuery())
					.withSourceFilter(new FetchSourceFilter(new String[]{REFERENCED_COMPONENT_ID}, null))
					.withPageable(ComponentService.LARGE_PAGE)
					.build();

			List<Long> descriptionIdsWithIndicators = new LongArrayList();
			try (SearchHitsIterator<ReferenceSetMember> memberResults = elasticsearchOperations.searchForStream(descriptionsWithInactivationIndicators, ReferenceSetMember.class)) {
				memberResults.forEachRemaining(hit -> descriptionIdsWithIndicators.add(Long.parseLong(hit.getContent().getReferencedComponentId())));
			}

			// find active descriptions without description inactivation indicators for inactive concepts
			BoolQuery.Builder queryDescriptionsWithoutInactivationIndicators = bool()
					.must(changesOnBranchOnly.getEntityBranchCriteria(Description.class))
					.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
					.must(termsQuery(Description.Fields.CONCEPT_ID, batch))
					.mustNot(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIdsWithIndicators));

			if (expectedExtensionModules != null) {
				queryDescriptionsWithoutInactivationIndicators.must(termsQuery(MODULE_ID, expectedExtensionModules));
			}

			NativeQuery descriptionsWithoutInactivationIndicators = new NativeQueryBuilder()
					.withQuery(queryDescriptionsWithoutInactivationIndicators.build()._toQuery())
					.withPageable(ComponentService.LARGE_PAGE)
					.build();

			try (SearchHitsIterator<Description> descriptions = elasticsearchOperations.searchForStream(descriptionsWithoutInactivationIndicators, Description.class)) {
				descriptions.forEachRemaining(hit -> {
					Description description = hit.getContent();
					// add description inactivation indicators
					ReferenceSetMember inactivation = new ReferenceSetMember(description.getModuleId(), Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET, description.getDescriptionId());
					inactivation.setAdditionalField("valueId", Concepts.CONCEPT_NON_CURRENT);
					inactivation.setConceptId(description.getConceptId());
					inactivation.setCreating(true);
					inactivation.markChanged();
					membersToSave.add(inactivation);
				});
			}
		}

		logger.info("{} descriptions found with inactive concepts but without concept non-current indicators", membersToSave.size());
		if (!membersToSave.isEmpty()) {
			//TODO What exception does this throw if we are unable to lock the branch?
			try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata("Concept non-current description inactivation"))) {
				conceptUpdateHelper.doSaveBatchComponents(membersToSave, ReferenceSetMember.class, commit);
				logger.info("Added {} concept non-current indicators for descriptions having inactive concepts. Member uuids: {}",
						membersToSave.size(), membersToSave.stream().map(ReferenceSetMember::getMemberId).collect(Collectors.toList()));
				commit.markSuccessful();
			}
		}
		logger.info("Completed description inactivation for inactive concepts for code system {} on branch {}", codeSystem.getShortName(), branchPath);
	}

	public void findAndUpdateLanguageRefsets(CodeSystem codeSystem) {
		logger.info("Start language reference set auto inactivation for code system {} on branch {}", codeSystem.getShortName(), codeSystem.getBranchPath());
		// find inactive descriptions
		List<Long> inactiveDescriptionIds = findInactiveDescriptions(codeSystem.getBranchPath());
		List<ReferenceSetMember> toInactivate = new ArrayList<>();
		List<ReferenceSetMember> toDelete = new ArrayList<>();

		// get active language refset members for inactive descriptions
		BranchCriteria changesOnBranchCriteria = versionControlHelper.getChangesOnBranchCriteria(codeSystem.getBranchPath());
		for (List<Long> batch : Iterables.partition(inactiveDescriptionIds, CLAUSE_LIMIT)) {
			NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(changesOnBranchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(ACTIVE, true))
							.must(termsQuery(REFERENCED_COMPONENT_ID, batch))
							.must(existsQuery(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH))))
					.withPageable(ComponentService.LARGE_PAGE);
			try (final SearchHitsIterator<ReferenceSetMember> activeMembers = elasticsearchOperations.searchForStream(searchQueryBuilder.build(), ReferenceSetMember.class)) {
				activeMembers.forEachRemaining(hit -> removeOrInactivate(hit.getContent(), toDelete, toInactivate));
			}
		}
		logger.info("{} language reference set members are to be inactivated: {}",
				toInactivate.size(), toInactivate.stream().map(ReferenceSetMember::getMemberId).collect(Collectors.toList()));
		logger.info("{} language reference set members are to be deleted: {}",
				toDelete.size(), toDelete.stream().map(ReferenceSetMember::getMemberId).collect(Collectors.toList()));
		// batch update
		List<ReferenceSetMember> toSave = new ArrayList<>();
		toSave.addAll(toInactivate);
		toSave.addAll(toDelete);
		if (!toSave.isEmpty()) {
			try (Commit commit = branchService.openCommit(codeSystem.getBranchPath(), branchMetadataHelper.getBranchLockMetadata("updating language refset members"))) {
				conceptUpdateHelper.doSaveBatchComponents(toSave, ReferenceSetMember.class, commit);
				commit.markSuccessful();
			}
		}
		logger.info("Completed language reference set auto inactivation for code system {} on branch {}", codeSystem.getShortName(), codeSystem.getBranchPath());
	}

	public void findAndUpdateAdditionalAxioms(CodeSystem codeSystem) {
		logger.info("Start additional axioms auto inactivation for code system {} on branch {}", codeSystem.getShortName(), codeSystem.getBranchPath());
		// find active axioms changed on extension MAIN branch
		Map<Long, List<ReferenceSetMember>> conceptToAxiomsMap = new HashMap<>();
		BranchCriteria changesOnBranchCriteria = versionControlHelper.getChangesOnBranchCriteria(codeSystem.getBranchPath());
		NativeQueryBuilder activeAxiomsQueryBuilder = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(changesOnBranchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))))
				.withPageable(ComponentService.LARGE_PAGE);
		try (SearchHitsIterator<ReferenceSetMember> activeAxioms = elasticsearchOperations.searchForStream(activeAxiomsQueryBuilder.build(), ReferenceSetMember.class)) {
			activeAxioms.forEachRemaining(hit -> conceptToAxiomsMap.computeIfAbsent(Long.parseLong(hit.getContent().getReferencedComponentId()), axioms -> new ArrayList<>()).add(hit.getContent()));
		}

		// check referenced components are still active
		if (conceptToAxiomsMap.isEmpty()) {
			return;
		}
		Set<Long> activeConceptIds = new LongOpenHashSet();
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(codeSystem.getBranchPath());
		NativeQueryBuilder activeConceptsQueryBuilder = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
						.must(termsQuery(Concept.Fields.CONCEPT_ID, conceptToAxiomsMap.keySet())))
				)
				.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
				.withPageable(ComponentService.LARGE_PAGE);
		try (SearchHitsIterator<Concept> activeConcepts = elasticsearchOperations.searchForStream(activeConceptsQueryBuilder.build(), Concept.class)) {
			activeConcepts.forEachRemaining(hit -> activeConceptIds.add(hit.getContent().getConceptIdAsLong()));
		}

		// inactivate additional axioms for publish components and delete for unpublished.
		List<ReferenceSetMember> toInactivate = new ArrayList<>();
		List<ReferenceSetMember> toDelete = new ArrayList<>();
		for (Map.Entry<Long, List<ReferenceSetMember>> entry : conceptToAxiomsMap.entrySet()) {
			if (!activeConceptIds.contains(entry.getKey())) {
				for (ReferenceSetMember axiom : entry.getValue()) {
					if (axiom.isReleased()) {
						axiom.setActive(false);
						axiom.markChanged();
						toInactivate.add(axiom);
					} else {
						axiom.markDeleted();
						toDelete.add(axiom);
					}
				}
			}
		}
		logger.info("{} published additional axioms are to be inactivated: {}",
				toInactivate.size(), toInactivate.stream().map(ReferenceSetMember::getMemberId).collect(Collectors.toList()));
		logger.info("{} unpublished additional axioms are to be deleted: {}",
				toDelete.size(), toDelete.stream().map(ReferenceSetMember::getMemberId).collect(Collectors.toList()));
		List<ReferenceSetMember> toSave = new ArrayList<>();
		toSave.addAll(toInactivate);
		toSave.addAll(toDelete);
		if (!toSave.isEmpty()) {
			try (Commit commit = branchService.openCommit(codeSystem.getBranchPath(), branchMetadataHelper.getBranchLockMetadata("additional axioms updating during upgrade"))) {
				conceptUpdateHelper.doSaveBatchComponents(toSave, ReferenceSetMember.class, commit);
				commit.markSuccessful();
			}
		}
		logger.info("Completed additional axioms auto inactivation for code system {} on branch {}", codeSystem.getShortName(), codeSystem.getBranchPath());
	}

	private void removeOrInactivate(ReferenceSetMember member, List<ReferenceSetMember> toDelete, List<ReferenceSetMember> toInactivate) {
		if (member != null) {
			if (member.isReleased()) {
				member.setActive(false);
				member.markChanged();
				toInactivate.add(member);
			} else {
				member.markDeleted();
				toDelete.add(member);
			}
		}
	}

	private List<Long> findInactiveDescriptions(String branchPath) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		List<Long> result = new LongArrayList();
		NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(Description.class))))
				.withFilter(termQuery(ACTIVE, false))
				.withSourceFilter(new FetchSourceFilter(new String[]{Description.Fields.DESCRIPTION_ID}, null))
				.withPageable(ComponentService.LARGE_PAGE);

		try (final SearchHitsIterator<Description> inactiveDescriptions = elasticsearchOperations.searchForStream(searchQueryBuilder.build(), Description.class)) {
			inactiveDescriptions.forEachRemaining(hit -> result.add(Long.parseLong(hit.getContent().getDescriptionId())));
		}
		return result;
	}

	private List<String> getExpectedExtensionModules(String branchPath) {
		Branch branch = branchService.findLatest(branchPath);
		if (branch == null) {
			return null;
		}

		Metadata metadata = branch.getMetadata();
		if (metadata == null) {
			return null;
		}

		List<String> expectedExtensionModules = metadata.getList(Config.EXPECTED_EXTENSION_MODULES);
		if (expectedExtensionModules == null || expectedExtensionModules.isEmpty()) {
			return null;
		}

		return expectedExtensionModules;
	}
}
