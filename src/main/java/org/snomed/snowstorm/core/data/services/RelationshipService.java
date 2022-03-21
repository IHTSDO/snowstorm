package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongComparators;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ConcreteValue;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.mrcm.MRCMLoader;
import org.snomed.snowstorm.mrcm.model.AttributeRange;
import org.snomed.snowstorm.mrcm.model.MRCM;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class RelationshipService extends ComponentService {
	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private MRCMLoader mrcmLoader;

	public Relationship findRelationship(String branchPath, String relationshipId) {
		final Page<Relationship> relationships = findRelationships(branchPath, relationshipId, null, null, null, null, null, null, null, null, PageRequest.of(0, 1));
		if (relationships.isEmpty()) {
			return null;
		}

		final Relationship relationship = relationships.getContent().get(0);
		setConcreteValueFromMRCM(branchPath, relationship);

		return relationship;
	}

	/**
	 * Set the ConcreteValue of each Relationship.
	 *
	 * @param branchPath    The branchPath to load active MRCM data from.
	 * @param relationships The Relationships to update.
	 * @throws RuntimeServiceException When there is an issue reading MRCM.
	 */
	public void setConcreteValueFromMRCM(String branchPath, Relationship... relationships) {
		MRCM mrcm;
		try {
			mrcm = mrcmLoader.loadActiveMRCMFromCache(branchPath);
		} catch (ServiceException e) {
			throw new RuntimeServiceException("Trouble loading active MRCM data.", e);
		}

		boolean isConcrete;
		boolean isInferred;
		List<AttributeRange> attributeRanges = mrcm.getAttributeRanges();
		for (Relationship relationship : relationships) {
			isConcrete = relationship.isConcrete();
			isInferred = relationship.getCharacteristicTypeId().equals(Relationship.CharacteristicType.inferred.getConceptId());
			if (isConcrete && isInferred) {
				String typeId = relationship.getTypeId();
				for (AttributeRange attributeRange : attributeRanges) {
					String referencedComponentId = attributeRange.getReferencedComponentId();
					if (typeId.equals(referencedComponentId)) {
						relationship.setConcreteValue(
								new ConcreteValue(relationship.getValueWithoutConcretePrefix(), attributeRange.getDataType())
						);
						break;
					}
				}
			}
		}
	}

	public Page<Relationship> findInboundRelationships(String conceptId, String branchPath, Relationship.CharacteristicType characteristicType) {
		return findRelationships(branchPath, null, true, null, null, null, null, conceptId, characteristicType, null, LARGE_PAGE);
	}

	public Page<Relationship> findRelationships(
			String branchPath,
			String relationshipId,
			Boolean active,
			String moduleId,
			String effectiveTime,
			String sourceId,
			String typeId,
			String destinationId,
			Relationship.CharacteristicType characteristicType,
			Integer group,
			PageRequest page) {

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		BoolQueryBuilder query = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(Relationship.class));

		if (relationshipId != null) {
			query.must(termQuery(Relationship.Fields.RELATIONSHIP_ID, relationshipId));
		}
		if (active != null) {
			query.must(termQuery(Relationship.Fields.ACTIVE, active));
		}
		if (moduleId != null) {
			query.must(termQuery(Relationship.Fields.MODULE_ID, moduleId));
		}
		if (effectiveTime != null) {
			query.must(termQuery(Relationship.Fields.EFFECTIVE_TIME, effectiveTime));
		}
		if (sourceId != null) {
			query.must(termQuery(Relationship.Fields.SOURCE_ID, sourceId));
		}
		if (typeId != null) {
			query.must(termQuery(Relationship.Fields.TYPE_ID, typeId));
		}
		if (destinationId != null) {
			query.must(termQuery(Relationship.Fields.DESTINATION_ID, destinationId));
		}
		if (group != null) {
			query.must(termQuery(Relationship.Fields.RELATIONSHIP_GROUP, group));
		}
		if (characteristicType != null) {
			query.must(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, characteristicType.getConceptId()));
		}

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(page)
				.build();
		searchQuery.setTrackTotalHits(true);

		SearchHits<Relationship> searchHits = elasticsearchOperations.search(searchQuery, Relationship.class);
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), page, searchHits.getTotalHits());
	}

	private List<Relationship> findRelationships(Set<String> relationshipIds, String branchPath) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		return elasticsearchOperations.search(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Relationship.class))
						.must(termsQuery(Relationship.Fields.RELATIONSHIP_ID, relationshipIds))
				)
				.withPageable(PageRequest.of(0, relationshipIds.size()))
				.build(), Relationship.class)
				.get().map(SearchHit::getContent).collect(Collectors.toList());
	}

	public List<Long> findRelationshipDestinationIds(Collection<Long> sourceConceptIds, Collection<Long> attributeTypeIds, BranchCriteria branchCriteria, boolean stated) {
		if (attributeTypeIds != null && attributeTypeIds.isEmpty()) {
			return Collections.emptyList();
		}

		Set<Long> destinationIds = new LongArraySet();
		if (sourceConceptIds == null) {
			NativeSearchQuery query = constructDestinationSearchQuery(null, attributeTypeIds, branchCriteria, stated);
			try (SearchHitsIterator<Relationship> stream = elasticsearchOperations.searchForStream(query, Relationship.class)) {
				stream.forEachRemaining(hit -> {
					if (hit.getContent().getDestinationId() != null) {
						destinationIds.add(parseLong(hit.getContent().getDestinationId()));
					}
				});
			}
		} else {
			for (List<Long> batch : Iterables.partition(sourceConceptIds, CLAUSE_LIMIT)) {
				NativeSearchQuery query = constructDestinationSearchQuery(batch, attributeTypeIds, branchCriteria, stated);
				try (SearchHitsIterator<Relationship> stream = elasticsearchOperations.searchForStream(query, Relationship.class)) {
					stream.forEachRemaining(hit -> {
						if (hit.getContent().getDestinationId() != null) {
							destinationIds.add(parseLong(hit.getContent().getDestinationId()));
						}
					});
				}
			}
		}
		// Stream search doesn't sort for us
		// Sorting meaningless but supports deterministic pagination
		List<Long> sortedIds = new LongArrayList(destinationIds);
		sortedIds.sort(LongComparators.OPPOSITE_COMPARATOR);
		return sortedIds;
	}

	private NativeSearchQuery constructDestinationSearchQuery(Collection<Long> sourceConceptIds, Collection<Long> attributeTypeIds, BranchCriteria branchCriteria, boolean stated) {
		BoolQueryBuilder boolQuery = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(Relationship.class))
				.must(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, stated ? Concepts.STATED_RELATIONSHIP : Concepts.INFERRED_RELATIONSHIP))
				.must(termQuery(Relationship.Fields.ACTIVE, true));

		if (attributeTypeIds != null) {
			boolQuery.must(termsQuery(Relationship.Fields.TYPE_ID, attributeTypeIds));
		}

		if (sourceConceptIds != null) {
			boolQuery.must(termsQuery(Relationship.Fields.SOURCE_ID, sourceConceptIds));
		}

		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery)
				.withFields(Relationship.Fields.DESTINATION_ID)
				.withPageable(LARGE_PAGE)
				.build();
	}

	/**
	 * Delete a relationship by id.
	 * @param relationshipId The id of the relationship to be deleted.
	 * @param branch The branch on which to make the change.
	 * @param force Delete the relationship even if it has been released.
	 */
	public void deleteRelationship(String relationshipId, String branch, boolean force) {
		Relationship relationship = findRelationship(BranchPathUriUtil.decodePath(branch), relationshipId);
		if (relationship == null) {
			throw new NotFoundException("Relationship not found.");
		}
		if (relationship.isReleased() && !force) {
			throw new IllegalArgumentException("Relationship is released so can not be deleted.");
		}
		try (Commit commit = branchService.openCommit(branch)) {
			relationship.markDeleted();
			conceptUpdateHelper.doSaveBatchRelationships(Collections.singleton(relationship), commit);
			commit.markSuccessful();
		}
	}

	/**
	 * Delete a set of relationships by id.
	 * @param relationshipIds The ids of the relationships to be deleted.
	 * @param branch The branch on which to make the change.
	 * @param force Delete the relationships even if they have been released.
	 */
	public void deleteRelationships(Set<String> relationshipIds, String branch, boolean force) {
		doDeleteRelationships(relationshipIds, branch, force, null);
	}

	public void deleteRelationshipsWithinCommit(Set<String> relationshipIds, Commit commit) {
		doDeleteRelationships(relationshipIds, commit.getBranch().getPath(), false, commit);
	}

	public void doDeleteRelationships(Set<String> relationshipIds, String branch, boolean force, Commit commit) {
		if (relationshipIds.isEmpty()) {
			return;
		}

		List<Relationship> matches = findRelationships(relationshipIds, BranchPathUriUtil.decodePath(branch));
		if (matches.size() != relationshipIds.size()) {
			List<String> matchedIds = matches.stream().map(Relationship::getRelationshipId).collect(Collectors.toList());
			Set<String> missingIds = new HashSet<>(relationshipIds);
			missingIds.removeAll(matchedIds);
			throw new NotFoundException(String.format("%s relationships not found on branch %s: %s", missingIds.size(), branch, missingIds));
		}

		for (Relationship relationship : matches) {
			if (relationship.isReleased() && !force) {
				throw new IllegalStateException(String.format("Relationship %s has been released and can't be deleted on branch %s.", relationship.getId(), branch));
			}
		}

		if (commit == null) {
			try (Commit newCommit = branchService.openCommit(branch)) {
				lowLevelDelete(matches, newCommit);
				newCommit.markSuccessful();
			}
		} else {
			lowLevelDelete(matches, commit);
		}
	}

	private void lowLevelDelete(List<Relationship> matches, Commit commit) {
		for (Relationship relationship : matches) {
			relationship.markDeleted();
		}
		conceptUpdateHelper.doSaveBatchRelationships(matches, commit);
	}

}
