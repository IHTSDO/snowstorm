package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

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

	public List<Relationship> findInboundRelationships(String conceptId, String branchPath, Relationship.CharacteristicType characteristicType) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		BoolQueryBuilder boolQuery = boolQuery()
				.must(branchCriteria)
				.must(termQuery("destinationId", conceptId))
				.must(termQuery("active", true));
		if (characteristicType != null) {
			boolQuery.must(termQuery("characteristicTypeId", characteristicType.getConceptId()));
		}

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery)
				.withPageable(LARGE_PAGE);
		List<Relationship> relationships = elasticsearchOperations.queryForList(queryBuilder.build(), Relationship.class);

		Set<String> sourceIds = relationships.stream().map(Relationship::getSourceId).collect(Collectors.toSet());
		Set<String> typeIds = relationships.stream().map(Relationship::getTypeId).collect(Collectors.toSet());

		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, Sets.union(sourceIds, typeIds)).getResultsMap();
		relationships.forEach(r -> {
			r.setSource(conceptMinis.get(r.getSourceId()));
			r.setType(conceptMinis.get(r.getTypeId()));
		});
		return relationships;
	}

	Set<Long> retrieveRelationshipDestinations(Collection<Long> sourceConceptIds, Collection<Long> attributeTypeIds, QueryBuilder branchCriteria, boolean stated) {
		if (attributeTypeIds != null && attributeTypeIds.isEmpty()) {
			return Collections.emptySet();
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
				.withSort(SortBuilders.fieldSort(Relationship.Fields.RELATIONSHIP_ID).order(SortOrder.DESC))// Meaningless but deterministic
				.withPageable(LARGE_PAGE)
				.build();

		Set<Long> destinationIds = new LongArraySet();
		try (CloseableIterator<Relationship> stream = elasticsearchOperations.stream(query, Relationship.class)) {
			stream.forEachRemaining(relationship -> destinationIds.add(parseLong(relationship.getDestinationId())));
		}

		return destinationIds;
	}
}
