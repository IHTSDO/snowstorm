package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class IntegrityService {

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService conceptService;

	public IntegrityIssueReport findChangedComponentsWithBadIntegrity(Branch branch) {

		if (branch.getPath().equals("MAIN")) {
			throw new RuntimeServiceException("This function can not be used on the MAIN branch. " +
					"Please use the full integrity check instead.");
		}

		TimerUtil timer = new TimerUtil("Changed component integrity check on " + branch.getPath());

		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branch);

		final Map<Long, Long> relationshipWithInactiveSource = new Long2LongOpenHashMap();
		final Map<Long, Long> relationshipWithInactiveType = new Long2LongOpenHashMap();
		final Map<Long, Long> relationshipWithInactiveDestination = new Long2LongOpenHashMap();

		// Find any active stated relationships using the concepts which have been deleted or inactivated on this branch
		Set<Long> deletedOrInactiveConcepts = findDeletedOrInactivatedConcepts(branch, branchCriteria);
		try (CloseableIterator<Relationship> changedOrDeletedConceptStream = elasticsearchTemplate.stream(
				new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(branchCriteria)
								.must(termsQuery(Relationship.Fields.ACTIVE, true))
								.mustNot(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP))
								.must(boolQuery()
										.should(termsQuery(Relationship.Fields.SOURCE_ID, deletedOrInactiveConcepts))
										.should(termsQuery(Relationship.Fields.TYPE_ID, deletedOrInactiveConcepts))
										.should(termsQuery(Relationship.Fields.DESTINATION_ID, deletedOrInactiveConcepts))
								)
						)
						.withPageable(LARGE_PAGE).build(),
				Relationship.class)) {
			changedOrDeletedConceptStream.forEachRemaining(relationship -> {
				if (deletedOrInactiveConcepts.contains(parseLong(relationship.getSourceId()))) {
					relationshipWithInactiveSource.put(parseLong(relationship.getRelationshipId()), parseLong(relationship.getSourceId()));
				}
				if (deletedOrInactiveConcepts.contains(parseLong(relationship.getTypeId()))) {
					relationshipWithInactiveType.put(parseLong(relationship.getRelationshipId()), parseLong(relationship.getTypeId()));
				}
				if (deletedOrInactiveConcepts.contains(parseLong(relationship.getDestinationId()))) {
					relationshipWithInactiveDestination.put(parseLong(relationship.getRelationshipId()), parseLong(relationship.getDestinationId()));
				}
			});
		}
		timer.checkpoint("Collect deleted or inactive concepts: " + deletedOrInactiveConcepts.size());

		// Gather all the concept ids used in active stated relationships which have been changed on this task
		Map<Long, Set<Long>> conceptUsedAsSourceInRelationships = new Long2ObjectOpenHashMap<>();
		Map<Long, Set<Long>> conceptUsedAsTypeInRelationships = new Long2ObjectOpenHashMap<>();
		Map<Long, Set<Long>> conceptUsedAsDestinationInRelationships = new Long2ObjectOpenHashMap<>();
		try (CloseableIterator<Relationship> relationshipStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(versionControlHelper.getBranchCriteriaUnpromotedChanges(branch))
								.must(termQuery(Relationship.Fields.ACTIVE, true))
								.mustNot(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP))
						)
						.withPageable(LARGE_PAGE)
						.build(),
				Relationship.class)) {
			relationshipStream.forEachRemaining(relationship -> {
				long relationshipId = parseLong(relationship.getRelationshipId());
				conceptUsedAsSourceInRelationships.computeIfAbsent(parseLong(relationship.getSourceId()), id -> new LongOpenHashSet()).add(relationshipId);
				conceptUsedAsTypeInRelationships.computeIfAbsent(parseLong(relationship.getTypeId()), id -> new LongOpenHashSet()).add(relationshipId);
				conceptUsedAsDestinationInRelationships.computeIfAbsent(parseLong(relationship.getDestinationId()), id -> new LongOpenHashSet()).add(relationshipId);
			});
		}

		// Of these concepts which are active
		Set<Long> conceptsRequiredActive = new LongOpenHashSet();
		conceptsRequiredActive.addAll(conceptUsedAsSourceInRelationships.keySet());
		conceptsRequiredActive.addAll(conceptUsedAsTypeInRelationships.keySet());
		conceptsRequiredActive.addAll(conceptUsedAsDestinationInRelationships.keySet());
		timer.checkpoint("Collect concepts referenced in changed relationships " + conceptsRequiredActive);

		Set<Long> activeConcepts = new LongOpenHashSet();
		try (CloseableIterator<Concept> activeConceptStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteriaUnpromotedChanges(branch))
						.must(termQuery(Concept.Fields.ACTIVE, true))
						.must(termsQuery(Concept.Fields.CONCEPT_ID, conceptsRequiredActive))
				)
				.build(), Concept.class)) {
			activeConceptStream.forEachRemaining(concept -> activeConcepts.add(concept.getConceptIdAsLong()));
		}
		timer.checkpoint("Collect active concepts referenced in changed relationships " + activeConcepts);

		// If any concepts not active add the relationships which use them to the report
		Set<Long> conceptsNotActive = new LongOpenHashSet(conceptsRequiredActive);
		conceptsNotActive.removeAll(activeConcepts);
		for (Long conceptNotActive : conceptsNotActive) {
			for (Long relationshipId : conceptUsedAsSourceInRelationships.getOrDefault(conceptNotActive, Collections.emptySet())) {
				relationshipWithInactiveSource.put(relationshipId, conceptNotActive);
			}
			for (Long relationshipId : conceptUsedAsTypeInRelationships.getOrDefault(conceptNotActive, Collections.emptySet())) {
				relationshipWithInactiveType.put(relationshipId, conceptNotActive);
			}
			for (Long relationshipId : conceptUsedAsDestinationInRelationships.getOrDefault(conceptNotActive, Collections.emptySet())) {
				relationshipWithInactiveDestination.put(relationshipId, conceptNotActive);
			}
		}
		timer.finish();

		return getReport(relationshipWithInactiveSource, relationshipWithInactiveType, relationshipWithInactiveDestination);
	}

	public IntegrityIssueReport findAllComponentsWithBadIntegrity(Branch branch, boolean stated) {

		final Map<Long, Long> relationshipWithInactiveSource = new Long2LongOpenHashMap();
		final Map<Long, Long> relationshipWithInactiveType = new Long2LongOpenHashMap();
		final Map<Long, Long> relationshipWithInactiveDestination = new Long2LongOpenHashMap();

		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branch);
		TimerUtil timer = new TimerUtil("Full integrity check on " + branch.getPath());
		Collection<Long> activeConcepts = conceptService.findAllActiveConcepts(branchCriteria);
		timer.checkpoint("Fetch active concepts");
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
		BoolQueryBuilder boolQueryBuilder = boolQuery();
		queryBuilder
				.withQuery(boolQueryBuilder
						.must(branchCriteria)
						.mustNot(
								boolQuery()
										.should(termsQuery(Relationship.Fields.SOURCE_ID, activeConcepts))
										.should(termsQuery(Relationship.Fields.TYPE_ID, activeConcepts))
										.should(termsQuery(Relationship.Fields.DESTINATION_ID, activeConcepts))
						)
				)
				.withPageable(LARGE_PAGE);

		if (stated) {
			boolQueryBuilder.mustNot(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP));
		} else {
			boolQueryBuilder.must(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP));
		}

		try (CloseableIterator<Relationship> relationshipStream = elasticsearchTemplate.stream(queryBuilder.build(), Relationship.class)) {
			relationshipStream.forEachRemaining(relationship -> {
				long relationshipId = parseLong(relationship.getRelationshipId());
				putIfInactive(relationship.getSourceId(), activeConcepts, relationshipId, relationshipWithInactiveSource);
				putIfInactive(relationship.getTypeId(), activeConcepts, relationshipId, relationshipWithInactiveType);
				putIfInactive(relationship.getDestinationId(), activeConcepts, relationshipId, relationshipWithInactiveDestination);
			});
		}
		timer.finish();

		return getReport(relationshipWithInactiveSource, relationshipWithInactiveType, relationshipWithInactiveDestination);
	}

	private IntegrityIssueReport getReport(Map<Long, Long> relationshipWithInactiveSource, Map<Long, Long> relationshipWithInactiveType, Map<Long, Long> relationshipWithInactiveDestination) {

		IntegrityIssueReport issueReport = new IntegrityIssueReport();

		if (!relationshipWithInactiveSource.isEmpty()) {
			issueReport.setRelationshipsWithMissingOrInactiveSource(relationshipWithInactiveSource);
		}
		if (!relationshipWithInactiveType.isEmpty()) {
			issueReport.setRelationshipsWithMissingOrInactiveType(relationshipWithInactiveType);
		}
		if (!relationshipWithInactiveDestination.isEmpty()) {
			issueReport.setRelationshipsWithMissingOrInactiveDestination(relationshipWithInactiveDestination);
		}

		return issueReport;
	}

	private void putIfInactive(String sourceId, Collection<Long> activeConcepts, long relationshipId, Map<Long, Long> relationshipWithInactiveSource) {
		long source = parseLong(sourceId);
		if (!activeConcepts.contains(source)) {
			relationshipWithInactiveSource.put(relationshipId, source);
		}
	}

	private Set<Long> findDeletedOrInactivatedConcepts(Branch branch, QueryBuilder branchCriteria) {
		// Find Concepts changed or deleted on this branch
		final Set<Long> changedOrDeletedConcepts = new LongOpenHashSet();
		try (CloseableIterator<Concept> changedOrDeletedConceptStream = elasticsearchTemplate.stream(
				new NativeSearchQueryBuilder()
						.withQuery(boolQuery().must(versionControlHelper.getBranchCriteriaUnpromotedChangesAndDeletions(branch)))
						.withPageable(LARGE_PAGE).build(),
				Concept.class)) {
			changedOrDeletedConceptStream.forEachRemaining(conceptState -> changedOrDeletedConcepts.add(conceptState.getConceptIdAsLong()));
		}

		// Of these concepts which are currently present and active
		final Set<Long> changedAndActiveConcepts = new LongOpenHashSet();
		try (CloseableIterator<Concept> changedOrDeletedConceptStream = elasticsearchTemplate.stream(
				new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(branchCriteria)
								.must(termsQuery(Concept.Fields.ACTIVE, true))
						)
						.withPageable(LARGE_PAGE).build(),
				Concept.class)) {
			changedOrDeletedConceptStream.forEachRemaining(conceptState -> changedAndActiveConcepts.add(conceptState.getConceptIdAsLong()));
		}

		// Therefore concepts deleted or inactive are:
		Set<Long> deletedOrInactiveConcepts = new LongOpenHashSet(changedOrDeletedConcepts);
		deletedOrInactiveConcepts.removeAll(changedAndActiveConcepts);
		return deletedOrInactiveConcepts;
	}
}
