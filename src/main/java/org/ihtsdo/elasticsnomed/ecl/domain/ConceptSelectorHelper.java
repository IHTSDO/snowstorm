package org.ihtsdo.elasticsnomed.ecl.domain;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.QueryConcept;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;

import java.util.List;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.*;

class ConceptSelectorHelper {

	static List<Long> fetch(BoolQueryBuilder query, QueryService queryService) {
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(LARGE_PAGE);

		List<Long> ids = new LongArrayList();
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
