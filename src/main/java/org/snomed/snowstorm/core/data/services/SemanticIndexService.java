package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.pojo.MapPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.util.*;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
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

	/**
	 * Load stated or inferred {@link QueryConcept} documents for the given concept ids in a single Elasticsearch query.
	 */
	public Map<Long, QueryConcept> loadQueryConcepts(String branch, Collection<Long> conceptIds, boolean stated) {
		if (conceptIds == null || conceptIds.isEmpty()) {
			return Collections.emptyMap();
		}
		if (conceptIds.size() > LARGE_PAGE.getPageSize()) {
			throw new TooCostlyException("Search concept ids over 10k is too costly.");
		}
		return fetchQueryConcepts(branch, conceptIds, stated);
	}

	private Map<Long, QueryConcept> fetchQueryConcepts(String branch, Collection<Long> conceptIds, boolean stated) {
		if (conceptIds.isEmpty()) {
			return Collections.emptyMap();
		}
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		NativeQuery query = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds))
						.must(termQuery(QueryConcept.Fields.STATED, stated))))
				.withSourceFilter(new FetchSourceFilter(true, new String[]{
						QueryConcept.Fields.CONCEPT_ID,
						QueryConcept.Fields.PARENTS,
						QueryConcept.Fields.ANCESTORS
				}, null))
				.withPageable(LARGE_PAGE)
				.build();
		Map<Long, QueryConcept> result = new HashMap<>();
		try (SearchHitsIterator<QueryConcept> stream = elasticsearchOperations.searchForStream(query, QueryConcept.class)) {
			stream.forEachRemaining(hit -> result.put(hit.getContent().getConceptIdL(), hit.getContent()));
		}
		return result;
	}
}
