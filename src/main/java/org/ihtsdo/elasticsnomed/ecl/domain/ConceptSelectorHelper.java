package org.ihtsdo.elasticsnomed.ecl.domain;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.QueryConcept;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;

import java.util.List;
import java.util.Set;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.*;

class ConceptSelectorHelper {

	static Set<Long> fetch(BoolQueryBuilder query, List<Long> conceptIdFilter, QueryService queryService, PageRequest pageRequest) {
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(pageRequest != null ? pageRequest : LARGE_PAGE);

		if (conceptIdFilter != null) {
			searchQueryBuilder.withFilter(boolQuery().must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, conceptIdFilter)));
		}

		Set<Long> ids = new LongOpenHashSet();
		try (CloseableIterator<QueryConcept> stream = queryService.stream(searchQueryBuilder.build())) {
			stream.forEachRemaining(c ->
					ids.add(c.getConceptId()));
		}
		return ids;
	}

	static BoolQueryBuilder getBranchAndStatedQuery(QueryBuilder branchCriteria, boolean stated) {
		return boolQuery()
					.must(branchCriteria)
					.must(termQuery(QueryConcept.STATED_FIELD, stated));
	}
}
