package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongComparators;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
public class RelationshipService extends ComponentService {

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService conceptService;

	public Relationship fetchRelationship(String branchPath, String relationshipId) {
		Page<Relationship> relationships = findRelationships(branchPath, relationshipId, null, null, null, null, null, null, null, null, PageRequest.of(0, 1));
		return relationships.getTotalElements() > 0 ? relationships.getContent().get(0) : null;
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

		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		BoolQueryBuilder query = boolQuery()
				.must(branchCriteria);

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

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(page);

		Page<Relationship> relationshipPage = elasticsearchOperations.queryForPage(queryBuilder.build(), Relationship.class);
		List<Relationship> relationships = relationshipPage.getContent();

		Set<String> sourceIds = relationships.stream().map(Relationship::getSourceId).collect(Collectors.toSet());
		Set<String> typeIds = relationships.stream().map(Relationship::getTypeId).collect(Collectors.toSet());

		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, Sets.union(sourceIds, typeIds)).getResultsMap();
		relationships.forEach(r -> {
			r.setSource(conceptMinis.get(r.getSourceId()));
			r.setType(conceptMinis.get(r.getTypeId()));
		});
		return relationshipPage;
	}

	List<Long> retrieveRelationshipDestinations(Collection<Long> sourceConceptIds, Collection<Long> attributeTypeIds, QueryBuilder branchCriteria, boolean stated) {
		if (attributeTypeIds != null && attributeTypeIds.isEmpty()) {
			return Collections.emptyList();
		}

		BoolQueryBuilder boolQuery = boolQuery()
				.must(branchCriteria)
				.must(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, stated ? Concepts.STATED_RELATIONSHIP : Concepts.INFERRED_RELATIONSHIP))
				.must(termsQuery(Relationship.Fields.ACTIVE, true));

		if (attributeTypeIds != null) {
			boolQuery.must(termsQuery(Relationship.Fields.TYPE_ID, attributeTypeIds));
		}

		if (sourceConceptIds != null) {
			boolQuery.must(termsQuery(Relationship.Fields.SOURCE_ID, sourceConceptIds));
		}

		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery)
				.withPageable(LARGE_PAGE)
				.build();

		Set<Long> destinationIds = new LongArraySet();
		try (CloseableIterator<Relationship> stream = elasticsearchOperations.stream(query, Relationship.class)) {
			stream.forEachRemaining(relationship -> destinationIds.add(parseLong(relationship.getDestinationId())));
		}

		// Stream search doesn't sort for us
		// Sorting meaningless but supports deterministic pagination
		List<Long> sortedIds = new LongArrayList(destinationIds);
		sortedIds.sort(LongComparators.OPPOSITE_COMPARATOR);
		return sortedIds;
	}
}
