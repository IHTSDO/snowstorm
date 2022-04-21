package org.snomed.snowstorm.ecl;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
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
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.RefinementBuilderImpl;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.util.*;
import java.util.function.Function;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.*;

public class ConceptSelectorHelper {

	// Used to force no match
	public static final String MISSING = "missing";
	public static final Long MISSING_LONG = 111L;

	private ConceptSelectorHelper() {
	}

	public static Page<Long> select(SExpressionConstraint sExpressionConstraint, RefinementBuilder refinementBuilder) {
		return select(sExpressionConstraint, refinementBuilder.getBranchCriteria(), refinementBuilder.isStated(),
				null, null, refinementBuilder.getEclContentService(), false);
	}

	public static Page<Long> select(SExpressionConstraint sExpressionConstraint, BranchCriteria branchCriteria, boolean stated,
			Collection<Long> conceptIdFilter, PageRequest pageRequest, ECLContentService eclContentService, boolean triedCache) {

		BoolQueryBuilder query = getBranchAndStatedQuery(branchCriteria.getEntityBranchCriteria(QueryConcept.class), stated);
		RefinementBuilder refinementBuilder = new RefinementBuilderImpl(query, branchCriteria, stated, eclContentService);

		// This can add an inclusionFilter to the refinementBuilder or run pre-selections to apply filters

		PrefetchResult prefetchResult = new PrefetchResult();
		sExpressionConstraint.addCriteria(refinementBuilder, prefetchResult::set, triedCache);

		// TODO: Member Filtering
		// List<MemberFilterConstraint> memberFilterConstraints = null;
		// if (sExpressionConstraint instanceof SubExpressionConstraint) {
		// memberFilterConstraints = subExpressionConstraint.getMemberFilterConstraints();
		// }

		if (prefetchResult.isSet()) {
			return getPage(pageRequest, prefetchResult.getIds());
		} else {
			return fetchIds(query, conceptIdFilter, refinementBuilder, pageRequest);
		}
	}

	public static BoolQueryBuilder getBranchAndStatedQuery(QueryBuilder branchCriteria, boolean stated) {
		return boolQuery()
				.must(branchCriteria)
				.must(termQuery(QueryConcept.Fields.STATED, stated));
	}

	public static Page<Long> fetchWildcardIds(BoolQueryBuilder query, Collection<Long> filterByConceptIds,
			PageRequest pageRequest, ECLContentService conceptSelector) {

		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withFields(QueryConcept.Fields.CONCEPT_ID);

		if (filterByConceptIds != null) {
			searchQueryBuilder.withFilter(termsQuery(QueryConcept.Fields.CONCEPT_ID, filterByConceptIds));
		}

		if (pageRequest != null) {
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
			Page<QueryConcept> queryConcepts = conceptSelector.queryForPage(searchQueryBuilder.build());
			List<Long> ids = queryConcepts.getContent().stream().map(QueryConcept::getConceptIdL).collect(toList());
			return new PageImpl<>(ids, pageRequest, queryConcepts.getTotalElements());
		} else {
			// Fetch all IDs
			searchQueryBuilder.withPageable(LARGE_PAGE);
			List<Long> addIds = new LongArrayList();
			try (SearchHitsIterator<QueryConcept> stream = conceptSelector.streamQueryResults(searchQueryBuilder.build())) {
				stream.forEachRemaining(hit -> {
					addIds.add(hit.getContent().getConceptIdL());
				});
			}

			// Stream search doesn't sort for us
			addIds.sort(LongComparators.OPPOSITE_COMPARATOR);

			return getPage(pageRequest, addIds);
		}
	}

	public static Page<Long> fetchIds(BoolQueryBuilder query, Collection<Long> filterByConceptIds, RefinementBuilder refinementBuilder, PageRequest pageRequest) {

		Function<QueryConcept, Boolean> inclusionFilter = refinementBuilder.getInclusionFilter();
		ECLContentService eclContentService = refinementBuilder.getEclContentService();

		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withFields(getRequiredFields(inclusionFilter));

		if (filterByConceptIds != null) {
			searchQueryBuilder.withFilter(termsQuery(QueryConcept.Fields.CONCEPT_ID, filterByConceptIds));
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
			Page<QueryConcept> queryConcepts = eclContentService.queryForPage(searchQueryBuilder.build());
			List<Long> ids = queryConcepts.getContent().stream().map(QueryConcept::getConceptIdL).collect(toList());
			return new PageImpl<>(ids, pageRequest, queryConcepts.getTotalElements());
		} else {
			// Fetch all IDs
			searchQueryBuilder.withPageable(LARGE_PAGE);
			List<Long> addIds = new LongArrayList();
			try (SearchHitsIterator<QueryConcept> stream = eclContentService.streamQueryResults(searchQueryBuilder.build())) {
				stream.forEachRemaining(hit -> {
					if (inclusionFilter == null || inclusionFilter.apply(hit.getContent())) {
						addIds.add(hit.getContent().getConceptIdL());
					}
				});
			}

			// Stream search doesn't sort for us
			addIds.sort(LongComparators.OPPOSITE_COMPARATOR);

			return getPage(pageRequest, addIds);
		}
	}

	public static PageImpl<Long> getPage(PageRequest pageRequest, List<Long> ids) {
		int total = ids.size();
		if (pageRequest != null) {
			int fromIndex = (int) pageRequest.getOffset();
			int toIndex = fromIndex + (pageRequest.getPageSize());
			if (toIndex > total) {
				toIndex = total;
			}
			return ids.isEmpty() || fromIndex >= total ? new PageImpl<>(Collections.emptyList(), pageRequest, total)
					: new PageImpl<>(ids.subList(fromIndex, toIndex), pageRequest, total);
		} else {
			return ids.isEmpty() ? new PageImpl<>(Collections.emptyList(), Pageable.unpaged(), total) : new PageImpl<>(ids, PageRequest.of(0, total), total);
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
