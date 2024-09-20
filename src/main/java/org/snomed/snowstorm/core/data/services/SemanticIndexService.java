package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.pojo.MapPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.lang.Long.parseLong;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;

@Service
public class SemanticIndexService {

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private VersionControlHelper versionControlHelper;


	public MapPage<Long, Set<Long>> findConceptReferences(String branch, Long conceptId, boolean stated, PageRequest pageRequest) {
		Map<Long, Set<Long>> referenceTypeToConceptMap = new HashMap<>();
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termQuery(QueryConcept.Fields.STATED, stated))
						.must(bool(bq -> bq// New bool query where at least one should must match
								.should(termQuery(QueryConcept.Fields.PARENTS, conceptId))
								.should(termQuery(QueryConcept.Fields.ATTR + "." + QueryConcept.ATTR_TYPE_WILDCARD, conceptId))))
				))
				.withPageable(pageRequest);
		String conceptIdString = conceptId.toString();
		SearchHits<QueryConcept> queryConcepts = elasticsearchOperations.search(queryBuilder.build(), QueryConcept.class);

		for (SearchHit<QueryConcept> hit : queryConcepts.getSearchHits()) {
			if (hit.getContent().getAncestors().contains(conceptId)) {
				referenceTypeToConceptMap.computeIfAbsent(Concepts.IS_A_LONG, id -> new LongOpenHashSet())
						.add(hit.getContent().getConceptIdL());
			} else {
				Map<String, Set<Object>> attributes = hit.getContent().getAttr();
				for (String attributeId : attributes.keySet()) {
					if (attributeId.equals(QueryConcept.ATTR_TYPE_WILDCARD)) {
						continue;
					}
					if (attributes.get(attributeId).contains(conceptIdString)) {
						referenceTypeToConceptMap.computeIfAbsent(parseLong(attributeId), id -> new LongOpenHashSet())
								.add(hit.getContent().getConceptIdL());
					}
				}
			}
		}
		return new MapPage<>(referenceTypeToConceptMap, pageRequest, queryConcepts.getTotalHits());
	}
}
