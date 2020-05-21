package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static io.kaicode.elasticvc.api.ComponentService.CLAUSE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.*;
import static org.snomed.snowstorm.core.data.domain.SnomedComponent.Fields.ACTIVE;

@Service
public class InactivationUpgradeService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public void findAndUpdateDescriptionsInactivation(CodeSystem codeSystem) {
		if (codeSystem == null) {
			throw new IllegalArgumentException("CodeSystem must not be null");
		}
		String branchPath = codeSystem.getBranchPath();
		logger.info("Start auto description inactivation for inactive concepts for code system {} on branch {}", codeSystem.getShortName(), branchPath);
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		// find inactive concept ids
		NativeSearchQuery inactiveConceptQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.ACTIVE, false)))
				.withFields(Concept.Fields.CONCEPT_ID)
				.withPageable(ComponentService.LARGE_PAGE)
				.build();
		List<Long> inactiveConceptIds = new LongArrayList();
		try (CloseableIterator<Concept> conceptResults = elasticsearchTemplate.stream(inactiveConceptQuery, Concept.class)) {
			conceptResults.forEachRemaining(concept -> inactiveConceptIds.add((concept.getConceptIdAsLong())));
		}

		List<ReferenceSetMember> membersToSave = new ArrayList<>();
		List<Description> descriptionsToDelete = new ArrayList<>();
		int total = 0;
		// find descriptions with inactivation indicators on the extension branch only
		BranchCriteria changesOnBranchOnly = versionControlHelper.getChangesOnBranchCriteria(codeSystem.getBranchPath());
		for (List<Long> batch : Iterables.partition(inactiveConceptIds, CLAUSE_LIMIT)) {
			NativeSearchQuery descriptionInactivationQuery = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(changesOnBranchOnly.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(REFSET_ID, Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET))
							.must(termQuery(getAdditionalFieldTextTypeMapping("valueid"), Concepts.CONCEPT_NON_CURRENT))
							.must(termsQuery(CONCEPT_ID, batch))
							.must(termQuery(ACTIVE, true)))
					.withFields(REFERENCED_COMPONENT_ID)
					.withPageable(ComponentService.LARGE_PAGE)
					.build();

			List<Long> descriptionIdsWithIndicators = new LongArrayList();
			try (CloseableIterator<ReferenceSetMember> memberResults = elasticsearchTemplate.stream(descriptionInactivationQuery, ReferenceSetMember.class)) {
				memberResults.forEachRemaining(member -> descriptionIdsWithIndicators.add(new Long(member.getReferencedComponentId())));
			}

			// find active descriptions without description inactivation indicators for inactive concepts
			NativeSearchQuery descriptionQuery = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(changesOnBranchOnly.getEntityBranchCriteria(Description.class))
							.must(termQuery(Description.Fields.ACTIVE, true))
							.must(termsQuery(Description.Fields.CONCEPT_ID, batch))
							.mustNot(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIdsWithIndicators)))
					.withPageable(ComponentService.LARGE_PAGE)
					.build();


			try (CloseableIterator<Description> descriptions = elasticsearchTemplate.stream(descriptionQuery, Description.class)) {
				descriptions.forEachRemaining(description -> updateOrDelete(description, membersToSave, descriptionsToDelete));
				total++;
			}
		}

		logger.info("{} descriptions found with inactive concepts but without concept non-current indicators", total);
		if (total > 0) {
			try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata("Concept non-current description inactivation"))) {
				if (!descriptionsToDelete.isEmpty()) {
					conceptUpdateHelper.doSaveBatchDescriptions(descriptionsToDelete, commit);
					logger.info("Deleted {} unpublished descriptions having inactive concepts.", descriptionsToDelete.size());
				}
				if (!membersToSave.isEmpty()) {
					conceptUpdateHelper.doSaveBatchComponents(membersToSave, ReferenceSetMember.class, commit);
					logger.info("Added {} concept non-current indicators for descriptions having inactive concepts.", membersToSave.size());
				}
				commit.markSuccessful();
			}
		}
		logger.info("Completed description inactivation for inactive concepts for code system {} on branch {}", codeSystem.getShortName(), branchPath);
	}

	private void updateOrDelete(Description description, List<ReferenceSetMember> inactivationMembersToSave, List<Description> descriptionsToDelete) {
		if (description.isReleased()) {
			// add description inactivation indicators
			ReferenceSetMember inactivation = new ReferenceSetMember(description.getModuleId(), Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET, description.getDescriptionId());
			inactivation.setAdditionalField(getAdditionalFieldTextTypeMapping("valueId"), Concepts.CONCEPT_NON_CURRENT);
			inactivation.setConceptId(description.getConceptId());
			inactivation.setCreating(true);
			inactivation.markChanged();
			inactivationMembersToSave.add(inactivation);
		} else {
			// delete descriptions if not published
			description.markDeleted();
			descriptionsToDelete.add(description);
		}
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
			NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(changesOnBranchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(ACTIVE, true))
							.must(termsQuery(REFERENCED_COMPONENT_ID, batch))
							.must(existsQuery(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH)))
					.withPageable(ComponentService.LARGE_PAGE);
			try (final CloseableIterator<ReferenceSetMember> activeMembers = elasticsearchTemplate.stream(searchQueryBuilder.build(), ReferenceSetMember.class)) {
				activeMembers.forEachRemaining(member -> removeOrInactivate(member, toDelete, toInactivate));
			}
		}
		logger.info("{} language reference set members are to be inactivated", toInactivate.size());
		logger.info("{} language reference set members are to be deleted", toDelete.size());
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
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class)))
				.withFilter(termQuery(ACTIVE, false))
				.withFields(Description.Fields.DESCRIPTION_ID)
				.withPageable(ComponentService.LARGE_PAGE);

		try (final CloseableIterator<Description> inactiveDescriptions = elasticsearchTemplate.stream(searchQueryBuilder.build(), Description.class)) {
			inactiveDescriptions.forEachRemaining(description -> result.add(new Long(description.getDescriptionId())));
		}
		return result;
	}
}
