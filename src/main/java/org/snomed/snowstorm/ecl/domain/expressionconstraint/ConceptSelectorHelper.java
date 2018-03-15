package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.*;

public class ConceptSelectorHelper {

	public static BoolQueryBuilder getBranchAndStatedQuery(QueryBuilder branchCriteria, boolean stated) {
		return boolQuery()
				.must(branchCriteria)
				.must(termQuery(QueryConcept.STATED_FIELD, stated));
	}

	static Page<Long> fetchIds(BoolQueryBuilder query, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		return fetchIds(query, conceptIdFilter, null, pageRequest, queryService);
	}

	public static Page<Long> fetchIds(BoolQueryBuilder query, Collection<Long> filterByConceptIds, Function<QueryConcept, Boolean> inclusionFilter, PageRequest pageRequest, QueryService queryService) {
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withFields(getRequiredFields(inclusionFilter));// This will cause the FastResultsMapper to be used

		if (filterByConceptIds != null) {
			searchQueryBuilder.withFilter(termsQuery(QueryConcept.CONCEPT_ID_FIELD, filterByConceptIds));
		}

		if (pageRequest != null) {
			// Fetch a page of IDs
			searchQueryBuilder.withPageable(pageRequest);
			Page<QueryConcept> queryConcepts = queryService.queryForPage(searchQueryBuilder.build());
			List<Long> ids = queryConcepts.getContent().stream().map(QueryConcept::getConceptId).collect(toList());
			return new PageImpl<>(ids, pageRequest, queryConcepts.getTotalElements());
		} else {
			// Fetch all IDs
			searchQueryBuilder.withPageable(LARGE_PAGE);
			List<Long> ids = new LongArrayList();
			try (CloseableIterator<QueryConcept> stream = queryService.stream(searchQueryBuilder.build())) {
				stream.forEachRemaining(c -> {
					if (inclusionFilter == null || inclusionFilter.apply(c)) {
						ids.add(c.getConceptId());
					}
				});
			}
			return ids.isEmpty() ? Page.empty() : new PageImpl<>(ids, PageRequest.of(0, ids.size()), ids.size());
		}
	}

	private static String[] getRequiredFields(Function<QueryConcept, Boolean> inclusionFilter) {
		Set<String> fields = Sets.newHashSet(QueryConcept.Fields.CONCEPT_ID);
		if (inclusionFilter != null) {
			fields.add(QueryConcept.Fields.ATTR_MAP);
		}
		return fields.toArray(new String[]{});
	}
}
