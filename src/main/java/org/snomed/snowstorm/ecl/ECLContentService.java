package org.snomed.snowstorm.ecl;

import io.kaicode.elasticvc.api.BranchCriteria;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.snomed.langauges.ecl.domain.ConceptReference;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.*;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.RelationshipService;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

/**
 * Service responsible for selecting SNOMED CT content on behalf of the ECL implementation.
 * Having a single class responsible for this gives good separation of concern and eases refactoring/maintenance.
 */
@Service
public class ECLContentService {

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	@Autowired
	private RelationshipService relationshipService;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private QueryService queryService;

	@Autowired
	@Lazy
	private ReferenceSetMemberService memberService;

	@Autowired
	@Lazy
	private ECLQueryService eclQueryService;

	public Page<QueryConcept> queryForPage(NativeSearchQuery searchQuery) {
		searchQuery.setTrackTotalHits(true);
		Pageable pageable = searchQuery.getPageable();
		SearchHits<QueryConcept> searchHits = elasticsearchTemplate.search(searchQuery, QueryConcept.class);
		return PageHelper.toSearchAfterPage(searchHits, pageable);
	}

	public SearchHitsIterator<QueryConcept> streamQueryResults(NativeSearchQuery searchQuery) {
		return elasticsearchTemplate.searchForStream(searchQuery, QueryConcept.class);
	}

	public Set<Long> findConceptIdsInReferenceSet(BranchCriteria branchCriteria, String referenceSetId) {
		return memberService.findConceptsInReferenceSet(branchCriteria, referenceSetId);
	}

	public List<Long> findRelationshipDestinationIds(Collection<Long> sourceConceptIds, List<Long> attributeTypeIds, BranchCriteria branchCriteria, boolean stated) {
		if (!stated) {
			// Use relationships - it's faster
			return relationshipService.findRelationshipDestinationIds(sourceConceptIds, attributeTypeIds, branchCriteria, false);
		}

		// For the stated view we'll use the semantic index to access relationships from both stated relationships or axioms.

		if (attributeTypeIds != null && attributeTypeIds.isEmpty()) {
			return Collections.emptyList();
		}

		BoolQueryBuilder boolQuery = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
				.must(termsQuery(QueryConcept.Fields.STATED, stated));

		if (attributeTypeIds != null) {
			BoolQueryBuilder shoulds = boolQuery();
			boolQuery.must(shoulds);
			for (Long attributeTypeId : attributeTypeIds) {
				if (!attributeTypeId.equals(Concepts.IS_A_LONG)) {
					shoulds.should(existsQuery(QueryConcept.Fields.ATTR + "." + attributeTypeId));
				}
			}
		}

		if (sourceConceptIds != null) {
			boolQuery.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, sourceConceptIds));
		}

		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery)
				.withPageable(LARGE_PAGE)
				.build();

		Set<Long> destinationIds = new LongArraySet();
		try (SearchHitsIterator<QueryConcept> stream = elasticsearchTemplate.searchForStream(query, QueryConcept.class)) {
			stream.forEachRemaining(hit -> {
				QueryConcept queryConcept = hit.getContent();
				if (attributeTypeIds != null) {
					for (Long attributeTypeId : attributeTypeIds) {
						if (attributeTypeId.equals(Concepts.IS_A_LONG)) {
							destinationIds.addAll(queryConcept.getParents());
						} else {
							queryConcept.getAttr().getOrDefault(attributeTypeId.toString(), Collections.emptySet()).forEach(id -> addDestinationId(id, destinationIds));
						}
					}
				} else {
					queryConcept.getAttr().values().forEach(destinationSet -> destinationSet.forEach(destinationId -> addDestinationId(destinationId, destinationIds)));
				}
			});
		}

		// Stream search doesn't sort for us
		// Sorting meaningless but supports deterministic pagination
		List<Long> sortedIds = new LongArrayList(destinationIds);
		sortedIds.sort(LongComparators.OPPOSITE_COMPARATOR);
		return sortedIds;
	}

	private void addDestinationId(Object destinationId, Set<Long> destinationIds) {
		if (destinationId instanceof String) {
			destinationIds.add(parseLong((String)destinationId));
		}
	}

	public Set<Long> findParentIds(BranchCriteria branchCriteria, boolean stated, Set<Long> singleton) {
		return queryService.findParentIds(branchCriteria, stated, singleton);
	}

	public Set<Long> findAncestorIdsAsUnion(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIds) {
		return queryService.findAncestorIdsAsUnion(branchCriteria, stated, conceptIds);
	}

	public Set<Long> applyConceptFilters(List<ConceptFilterConstraint> conceptFilters, Set<Long> conceptIdsToFilter, String path, BranchCriteria branchCriteria, boolean stated) {

		BoolQueryBuilder superQuery = branchCriteria.getEntityBranchCriteria(Concept.class);
		for (ConceptFilterConstraint conceptFilter : conceptFilters) {
			BoolQueryBuilder conceptFilterQuery = boolQuery();

			// Active filter
			for (ActiveFilter activeFilter : orEmpty(conceptFilter.getActiveFilters())) {
				conceptFilterQuery.must(termQuery(SnomedComponent.Fields.ACTIVE, activeFilter.isActive()));
			}

			// Definition status filter
			applyFieldFilters(conceptFilter.getDefinitionStatusFilters(), conceptFilterQuery, path, branchCriteria, stated);

			// Module filter
			applyFieldFilters(conceptFilter.getModuleFilters(), conceptFilterQuery, path, branchCriteria, stated);

			// EffectiveTimeFilter
			for (EffectiveTimeFilter effectiveTimeFilter : orEmpty(conceptFilter.getEffectiveTimeFilters())) {
				TimeComparisonOperator operator = effectiveTimeFilter.getOperator();
				Set<Integer> effectiveTimes = effectiveTimeFilter.getEffectiveTime();
				BoolQueryBuilder query = boolQuery();
				String effectiveTimeField = SnomedComponent.Fields.EFFECTIVE_TIME;
				switch (operator) {
					case EQUAL:
						query.must(termsQuery(effectiveTimeField, effectiveTimes));
						break;
					case NOT_EQUAL:
						query.mustNot(termsQuery(effectiveTimeField, effectiveTimes));
						break;
					case LESS_THAN_OR_EQUAL:
						for (Integer effectiveTime : effectiveTimes) {
							query.must(rangeQuery(effectiveTimeField).lte(effectiveTime));
						}
						break;
					case LESS_THAN:
						for (Integer effectiveTime : effectiveTimes) {
							query.must(rangeQuery(effectiveTimeField).lt(effectiveTime));
						}
						break;
					case GREATER_THAN_OR_EQUAL:
						for (Integer effectiveTime : effectiveTimes) {
							query.must(rangeQuery(effectiveTimeField).gte(effectiveTime));
						}
						break;
					case GREATER_THAN:
						for (Integer effectiveTime : effectiveTimes) {
							query.must(rangeQuery(effectiveTimeField).gt(effectiveTime));
						}
						break;
				}
				conceptFilterQuery.must(query);
			}
			superQuery.must(conceptFilterQuery);
		}

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(superQuery)
				.withFilter(termsQuery(Concept.Fields.CONCEPT_ID, conceptIdsToFilter))
				.withFields(Concept.Fields.CONCEPT_ID)
				.withPageable(LARGE_PAGE);
		Set<Long> conceptIds = new LongOpenHashSet();
		try (SearchHitsIterator<Concept> stream = elasticsearchTemplate.searchForStream(queryBuilder.build(), Concept.class)) {
			stream.forEachRemaining(hit -> conceptIds.add(hit.getContent().getConceptIdAsLong()));
		}

		return conceptIds;
	}

	private void applyFieldFilters(List<FieldFilter> fieldFilters, BoolQueryBuilder conceptFilterQuery, String path, BranchCriteria branchCriteria, boolean stated) {
		for (FieldFilter fieldFilter : orEmpty(fieldFilters)) {
			Set<String> values = null;
			SubExpressionConstraint subExpressionConstraint = fieldFilter.getSubExpressionConstraint();
			if (subExpressionConstraint != null) {
				Optional<Page<Long>> concepts = ConceptSelectorHelper.select((SSubExpressionConstraint) subExpressionConstraint, path, branchCriteria, stated, null, null, this);
				if (concepts.isPresent()) {
					values = concepts.get().get().map(Object::toString).collect(Collectors.toSet());
				}
			} else {
				values = fieldFilter.getConceptReferences().stream().map(ConceptReference::getConceptId).collect(Collectors.toSet());
			}
			if (values != null) {
				TermsQueryBuilder termsQuery = termsQuery(fieldFilter.getField(), values);
				if (fieldFilter.isEquals()) {
					conceptFilterQuery.must(termsQuery);
				} else {
					conceptFilterQuery.mustNot(termsQuery);
				}
			}
		}
	}

	public SortedMap<Long, Long> applyDescriptionFilter(Collection<Long> conceptIds, List<TermFilter> termFilters, List<LanguageFilter> languageFilters,
			List<DescriptionTypeFilter> descriptionTypeFilters, List<DialectFilter> dialectFilters, BranchCriteria branchCriteria) {

		return descriptionService.applyDescriptionFilter(conceptIds, termFilters, languageFilters, descriptionTypeFilters, dialectFilters, branchCriteria, eclQueryService);
	}
}
