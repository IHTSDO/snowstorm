package org.snomed.snowstorm.ecl;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparators;

import org.apache.commons.lang3.NotImplementedException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

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
				.must(termQuery(QueryConcept.Fields.STATED, stated));
	}

	public static Page<Long> fetchIds(BoolQueryBuilder query, Collection<Long> filterByConceptIds, Function<QueryConcept, Boolean> inclusionFilter, PageRequest pageRequest, QueryService queryService) {
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withFields(getRequiredFields(inclusionFilter));

		if (filterByConceptIds != null) {
			searchQueryBuilder.withFilter(termsQuery(QueryConcept.Fields.CONCEPT_ID, filterByConceptIds));
		}
		if (inclusionFilter == null) {
			searchQueryBuilder.withFields(QueryConcept.Fields.CONCEPT_ID);
		}

		if (pageRequest != null && inclusionFilter == null) {
			// Fetch a page of IDs
			if (Sort.unsorted().equals(pageRequest.getSort())
					|| pageRequest.getSort().getOrderFor(QueryConcept.Fields.CONCEPT_ID) == null) {
				//Check the page request isn't a SearchAfter because we explicitly use page number here
				if (pageRequest instanceof SearchAfterPageRequest) {
					throw new NotImplementedException("searchAfter in ConceptSelector must be accompanied with a sort order");
				}
				
				searchQueryBuilder.withSort(getDefaultSortForQueryConcept());
				searchQueryBuilder.withPageable(PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize()));
			} else {
				searchQueryBuilder.withPageable(pageRequest);
			}
			Page<QueryConcept> queryConcepts = queryService.queryForPage(searchQueryBuilder.build());
			List<Long> ids = queryConcepts.getContent().stream().map(QueryConcept::getConceptIdL).collect(toList());
			return new PageImpl<>(ids, pageRequest, queryConcepts.getTotalElements());
		} else {
			// Fetch all IDs
			searchQueryBuilder.withPageable(LARGE_PAGE);
			List<Long> ids = new LongArrayList();
			try (SearchHitsIterator<QueryConcept> stream = queryService.streamQueryResults(searchQueryBuilder.build())) {
				stream.forEachRemaining(hit -> {
					if (inclusionFilter == null || inclusionFilter.apply(hit.getContent())) {
						ids.add(hit.getContent().getConceptIdL());
					}
				});
			}

			// Stream search doesn't sort for us
			ids.sort(LongComparators.OPPOSITE_COMPARATOR);

			int total = ids.size();
			if (pageRequest != null) {
				int fromIndex = (int) pageRequest.getOffset();
				int toIndex = fromIndex + (pageRequest.getPageSize());
				if (toIndex > total) {
					toIndex = total;
				}
				return ids.isEmpty() || fromIndex >= total ? Page.empty() : new PageImpl<>(ids.subList(fromIndex, toIndex), pageRequest, total);
			} else {
				return ids.isEmpty() ? Page.empty() : new PageImpl<>(ids, PageRequest.of(0, total), total);
			}
		}
	}

	public static FieldSortBuilder getDefaultSortForQueryConcept() {
		return SortBuilders.fieldSort(QueryConcept.Fields.CONCEPT_ID).order(SortOrder.DESC);
	}

	public static FieldSortBuilder getDefaultSortForConcept() {
		return SortBuilders.fieldSort(Concept.Fields.CONCEPT_ID).order(SortOrder.DESC);
	}

	private static String[] getRequiredFields(Function<QueryConcept, Boolean> inclusionFilter) {
		Set<String> fields = Sets.newHashSet(QueryConcept.Fields.CONCEPT_ID);
		if (inclusionFilter != null) {
			fields.add(QueryConcept.Fields.ATTR_MAP);
		}
		return fields.toArray(new String[]{});
	}
}
