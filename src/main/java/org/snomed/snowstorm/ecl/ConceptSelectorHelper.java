package org.snomed.snowstorm.ecl;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparators;
import org.apache.commons.lang3.NotImplementedException;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.RefinementBuilderImpl;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SRefinedExpressionConstraint;
import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

import java.util.*;
import java.util.function.Function;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.util.stream.Collectors.toList;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.core.util.SearchAfterQueryHelper.updateQueryWithSearchAfter;

public class ConceptSelectorHelper {

	// Used to force no match
	public static final String MISSING = "missing";
	public static final Long MISSING_LONG = 111L;

	public static final Function<Long, Object[]> CONCEPT_ID_SEARCH_AFTER_EXTRACTOR =
			conceptId -> conceptId == null ? null : SearchAfterHelper.convertToTokenAndBack(new Object[]{conceptId});

	private ConceptSelectorHelper() {
	}

	public static Page<Long> select(SExpressionConstraint sExpressionConstraint, RefinementBuilder refinementBuilder) {
		return select(sExpressionConstraint, refinementBuilder.getBranchCriteria(), refinementBuilder.isStated(),
				null, null, refinementBuilder.getEclContentService(), false);
	}

	public static Page<Long> select(SExpressionConstraint sExpressionConstraint, BranchCriteria branchCriteria, boolean stated,
			Collection<Long> conceptIdFilter, PageRequest pageRequest, ECLContentService eclContentService, boolean triedCache) {

		BoolQuery.Builder queryBuilder = bool().must(getBranchAndStatedQuery(branchCriteria.getEntityBranchCriteria(QueryConcept.class), stated));
		// This can add an inclusionFilter to the refinementBuilder or run pre-selections to apply filters
		RefinementBuilder refinementBuilder = new RefinementBuilderImpl(queryBuilder, branchCriteria, stated, eclContentService);
		// Check if it should prefetch memberOfQueryResults
		if (refinementBuilder.shouldPrefetchMemberOfQueryResults() == null) {
			refinementBuilder.setShouldPrefetchMemberOfQueryResults(!(sExpressionConstraint instanceof SRefinedExpressionConstraint));
		}
		PrefetchResult prefetchResult = new PrefetchResult();
		sExpressionConstraint.addCriteria(refinementBuilder, prefetchResult::set, triedCache);

		if (prefetchResult.isSet()) {
			return getPage(pageRequest, prefetchResult.getIds());
		} else {
			return fetchIds(queryBuilder.build()._toQuery(), conceptIdFilter, refinementBuilder, pageRequest);
		}
	}

	public static Query getBranchAndStatedQuery(Query branchCriteria, boolean stated) {
		return bool(b -> b
				.must(branchCriteria)
				.must(termQuery(QueryConcept.Fields.STATED, stated)));
	}

	public static Page<Long> fetchWildcardIds(Query query, Collection<Long> filterByConceptIds,
			PageRequest pageRequest, ECLContentService conceptSelector) {

		NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder()
				.withQuery(query)
				.withSourceFilter(new FetchSourceFilter(true, new String[]{QueryConcept.Fields.CONCEPT_ID}, null));

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
			Page<QueryConcept> queryConcepts = conceptSelector.queryForPage(updateQueryWithSearchAfter(searchQueryBuilder.build(), pageRequest));
			List<Long> ids = queryConcepts.getContent().stream().map(QueryConcept::getConceptIdL).collect(toList());
			return new PageImpl<>(ids, pageRequest, queryConcepts.getTotalElements());
		} else {
			// Fetch all IDs
			searchQueryBuilder.withPageable(LARGE_PAGE);
			List<Long> addIds = new LongArrayList();
			try (SearchHitsIterator<QueryConcept> stream = conceptSelector.streamQueryResults(searchQueryBuilder.build())) {
				stream.forEachRemaining(hit -> addIds.add(hit.getContent().getConceptIdL()));
			}

			// Stream search doesn't sort for us
			addIds.sort(LongComparators.OPPOSITE_COMPARATOR);

			return getPage(pageRequest, addIds);
		}
	}

	public static Page<Long> fetchIds(Query query, Collection<Long> filterByConceptIds, RefinementBuilder refinementBuilder, PageRequest pageRequest) {

		Function<QueryConcept, Boolean> inclusionFilter = refinementBuilder.getInclusionFilter();
		ECLContentService eclContentService = refinementBuilder.getEclContentService();

		NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder()
				.withQuery(query)
				.withSourceFilter(new FetchSourceFilter(true, getRequiredFields(inclusionFilter), null));

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
			Page<QueryConcept> queryConcepts = eclContentService.queryForPage(updateQueryWithSearchAfter(searchQueryBuilder.build(), pageRequest));
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

	public static Page<Long> getPage(PageRequest pageRequest, List<Long> ids) {
		int total = ids.size();
		if (pageRequest != null) {
			return PageHelper.fullListToPage(ids, pageRequest, CONCEPT_ID_SEARCH_AFTER_EXTRACTOR);
		} else {
			return ids.isEmpty() ? new PageImpl<>(Collections.emptyList(), Pageable.unpaged(), total) : new PageImpl<>(ids, PageRequest.of(0, total), total);
		}
	}

	public static SortOptions getDefaultSortForQueryConcept() {
		return SortOptions.of(s -> s.field(f -> f.field(QueryConcept.Fields.CONCEPT_ID).order(SortOrder.Desc)));
	}

	public static SortOptions getDefaultSortForConcept() {
		return SortOptions.of(s -> s.field(f -> f.field(Concept.Fields.CONCEPT_ID).order(SortOrder.Desc)));
	}

	private static String[] getRequiredFields(Function<QueryConcept, Boolean> inclusionFilter) {
		Set<String> fields = Sets.newHashSet(QueryConcept.Fields.CONCEPT_ID);
		if (inclusionFilter != null) {
			fields.add(QueryConcept.Fields.ATTR_MAP);
		}
		return fields.toArray(new String[]{});
	}

}
