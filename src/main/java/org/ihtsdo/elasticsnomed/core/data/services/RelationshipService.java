package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.ConceptMini;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

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

		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, Sets.union(sourceIds, typeIds));
		relationships.forEach(r -> {
			r.setSource(conceptMinis.get(r.getSourceId()));
			r.setType(conceptMinis.get(r.getTypeId()));
		});
		return relationships;
	}
}
