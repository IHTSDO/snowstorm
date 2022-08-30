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
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.RelationshipService;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.core.util.SearchAfterPageImpl;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
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

	private SExpressionConstraint historyMaxECL;

	private static final List<Long> HISTORY_PROFILE_MIN = Collections.singletonList(parseLong(Concepts.REFSET_SAME_AS_ASSOCIATION));
	private static final List<Long> HISTORY_PROFILE_MOD = List.of(
			parseLong(Concepts.REFSET_SAME_AS_ASSOCIATION),
			parseLong(Concepts.REFSET_REPLACED_BY_ASSOCIATION),
			parseLong(Concepts.REFSET_WAS_A_ASSOCIATION),
			parseLong(Concepts.REFSET_PARTIALLY_EQUIVALENT_TO_ASSOCIATION));

	@PostConstruct
	public void init() {
		historyMaxECL = (SExpressionConstraint) eclQueryService.createQuery("< 900000000000522004 |Historical association reference set|");
	}

	public List<Long> fetchAllIdsWithCaching(SSubExpressionConstraint sSubExpressionConstraint, BranchCriteria branchCriteria, boolean stated) {
		return eclQueryService.doSelectConceptIds(sSubExpressionConstraint, branchCriteria, stated, null, null).getContent();
	}

	public Page<QueryConcept> queryForPage(NativeSearchQuery searchQuery) {
		searchQuery.setTrackTotalHits(true);
		Pageable pageable = searchQuery.getPageable();
		SearchHits<QueryConcept> searchHits = elasticsearchTemplate.search(searchQuery, QueryConcept.class);
		return PageHelper.toSearchAfterPage(searchHits, pageable);
	}

	public SearchHitsIterator<QueryConcept> streamQueryResults(NativeSearchQuery searchQuery) {
		return elasticsearchTemplate.searchForStream(searchQuery, QueryConcept.class);
	}

	public SearchAfterPage<ReferenceSetMember> findReferenceSetMembers(Collection<Long> refsets, List<MemberFilterConstraint> memberFilterConstraints,
			List<String> memberFieldsToReturn, Collection<Long> conceptIdFilter, boolean stated, BranchCriteria branchCriteria, PageRequest pageRequest,
			ECLContentService eclContentService) {

		BoolQueryBuilder masterMemberQuery = buildECLMemberQuery(memberFilterConstraints, stated, branchCriteria);
		SearchAfterPageImpl<ReferenceSetMember> emptyPage = new SearchAfterPageImpl<>(Collections.emptyList(), pageRequest, 0, new Object[]{});

		if (conceptIdFilter != null) {
			if (conceptIdFilter.isEmpty()) {
				// No rows can match
				return emptyPage;
			}
			masterMemberQuery.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIdFilter));
		}

		if (refsets != null) {
			if (refsets.isEmpty()) {
				// No rows can match
				return emptyPage;
			}
			masterMemberQuery.filter(termsQuery(ReferenceSetMember.Fields.REFSET_ID, refsets));
		}

		return memberService.findMembersForECLResponse(masterMemberQuery, memberFilterConstraints, memberFieldsToReturn, stated, branchCriteria, pageRequest, eclContentService);
	}

	public Set<Long> findConceptIdsInReferenceSet(Collection<Long> referenceSetIds, List<MemberFilterConstraint> memberFilterConstraints, RefinementBuilder refinementBuilder) {
		BoolQueryBuilder masterMemberQuery = buildECLMemberQuery(memberFilterConstraints, refinementBuilder.isStated(), refinementBuilder.getBranchCriteria());
		return memberService.findConceptsInReferenceSet(referenceSetIds, memberFilterConstraints, refinementBuilder, masterMemberQuery);
	}

	private BoolQueryBuilder buildECLMemberQuery(List<MemberFilterConstraint> memberFilterConstraints, boolean stated, BranchCriteria branchCriteria) {
		BoolQueryBuilder masterMemberQuery = branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class);

		for (MemberFilterConstraint memberFilterConstraint : orEmpty(memberFilterConstraints)) {

			// Module filter
			applyFieldFilters(memberFilterConstraint.getModuleFilters(), masterMemberQuery, branchCriteria, stated, "<<900000000000443000 |Module (core metadata concept)|");

			// Effective time filters
			applyEffectiveTimeFilters(memberFilterConstraint.getEffectiveTimeFilters(), masterMemberQuery);

			// Active filters
			applyActiveFilters(memberFilterConstraint.getActiveFilters(), masterMemberQuery);
		}
		if (memberFilterConstraints == null) {
			applyActiveFilters(null, masterMemberQuery);// Apply active defaults
		}
		return masterMemberQuery;
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

	public Set<Long> applyConceptFilters(List<ConceptFilterConstraint> conceptFilters, Set<Long> conceptIdsToFilter, BranchCriteria branchCriteria, boolean stated) {

		BoolQueryBuilder superQuery = branchCriteria.getEntityBranchCriteria(Concept.class);
		for (ConceptFilterConstraint conceptFilter : conceptFilters) {
			BoolQueryBuilder conceptFilterQuery = boolQuery();

			// Active filter
			applyActiveFilters(conceptFilter.getActiveFilters(), conceptFilterQuery);

			// Definition status filter
			applyFieldFilters(conceptFilter.getDefinitionStatusFilters(), conceptFilterQuery, branchCriteria, stated, "<<900000000000444006 |Definition status (core metadata concept)|");

			// Module filter
			applyFieldFilters(conceptFilter.getModuleFilters(), conceptFilterQuery, branchCriteria, stated, "<<900000000000443000 |Module (core metadata concept)|");

			// EffectiveTimeFilter
			applyEffectiveTimeFilters(conceptFilter.getEffectiveTimeFilters(), conceptFilterQuery);

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

	public SortedMap<Long, Long> applyDescriptionFilter(Collection<Long> conceptIds, DescriptionFilterConstraint descriptionFilter, BranchCriteria branchCriteria, boolean stated) {

		BoolQueryBuilder masterDescriptionQuery = boolQuery();

		// Module filter
		applyFieldFilters(descriptionFilter.getModuleFilters(), masterDescriptionQuery, branchCriteria, stated, "<<900000000000443000 |Module (core metadata concept)|");

		// Effective time filters
		applyEffectiveTimeFilters(descriptionFilter.getEffectiveTimeFilters(), masterDescriptionQuery);

		// Active filters
		applyActiveFilters(descriptionFilter.getActiveFilters(), masterDescriptionQuery);

		List<TermFilter> termFilters = orEmpty(descriptionFilter.getTermFilters());
		List<LanguageFilter> languageFilters = orEmpty(descriptionFilter.getLanguageFilters());
		List<DescriptionTypeFilter> descriptionTypeFilters = orEmpty(descriptionFilter.getDescriptionTypeFilters());
		List<DialectFilter> dialectFilters = orEmpty(descriptionFilter.getDialectFilters());

		return descriptionService.applyDescriptionFilter(conceptIds, termFilters, languageFilters, descriptionTypeFilters, dialectFilters,
				branchCriteria, eclQueryService, masterDescriptionQuery);
	}

	private void applyFieldFilters(List<FieldFilter> fieldFilters, BoolQueryBuilder filterQuery, BranchCriteria branchCriteria, boolean stated, String eclContentFilter) {
		for (FieldFilter fieldFilter : orEmpty(fieldFilters)) {
			Set<String> values = null;
			SubExpressionConstraint subExpressionConstraint = fieldFilter.getSubExpressionConstraint();
			if (subExpressionConstraint != null) {
				SSubExpressionConstraint sSubExpressionConstraint = (SSubExpressionConstraint) subExpressionConstraint;
				if (!sSubExpressionConstraint.isUnconstrained()) {
					SExpressionConstraint expressionConstraint = sSubExpressionConstraint;
					if (eclContentFilter != null) {
						expressionConstraint = (SExpressionConstraint) eclQueryService.createQuery(String.format("(%s) AND (%s)", expressionConstraint.toEclString(), eclContentFilter));
					}
					List<Long> concepts = ConceptSelectorHelper.select(
							expressionConstraint, branchCriteria, stated, null, null, this, false).getContent();
					values = concepts.stream().map(Object::toString).collect(Collectors.toSet());
				}
			} else {
				values = fieldFilter.getConceptReferences().stream().map(ConceptReference::getConceptId).collect(Collectors.toSet());
			}
			String field = fieldFilter.getField();
			if (values != null) {
				TermsQueryBuilder termsQuery = termsQuery(field, values);
				if (fieldFilter.isEquals()) {
					filterQuery.must(termsQuery);
				} else {
					filterQuery.mustNot(termsQuery);
				}
			} else {
				filterQuery.must(existsQuery(field));
			}
		}
	}

	private void applyActiveFilters(List<ActiveFilter> activeFilters, BoolQueryBuilder componentFilterQuery) {
		activeFilters = orEmpty(activeFilters);
		if (activeFilters.isEmpty()) {
			componentFilterQuery.must(termQuery(SnomedComponent.Fields.ACTIVE, true));
		} else {
			for (ActiveFilter activeFilter : activeFilters) {
				componentFilterQuery.must(termQuery(SnomedComponent.Fields.ACTIVE, activeFilter.isActive()));
			}
		}
	}

	private void applyEffectiveTimeFilters(List<EffectiveTimeFilter> effectiveTimeFilters, BoolQueryBuilder componentFilterQuery) {
		for (EffectiveTimeFilter effectiveTimeFilter : orEmpty(effectiveTimeFilters)) {
			NumericComparisonOperator operator = effectiveTimeFilter.getOperator();
			List<Integer> effectiveTimes = effectiveTimeFilter.getEffectiveTime();
			BoolQueryBuilder query = boolQuery();
			String effectiveTimeField = SnomedComponent.Fields.EFFECTIVE_TIME;
			addNumericConstraint(operator, effectiveTimeField, effectiveTimes, query);
			componentFilterQuery.must(query);
		}
	}

	public static void addNumericConstraint(NumericComparisonOperator operator, String field, Collection<? extends Number> values, BoolQueryBuilder query) {
		switch (operator) {
			case EQUAL:
				query.must(termsQuery(field, values));
				break;
			case NOT_EQUAL:
				query.mustNot(termsQuery(field, values));
				break;
			case LESS_THAN_OR_EQUAL:
				for (Number effectiveTime : values) {
					query.must(rangeQuery(field).lte(effectiveTime));
				}
				break;
			case LESS_THAN:
				for (Number effectiveTime : values) {
					query.must(rangeQuery(field).lt(effectiveTime));
				}
				break;
			case GREATER_THAN_OR_EQUAL:
				for (Number effectiveTime : values) {
					query.must(rangeQuery(field).gte(effectiveTime));
				}
				break;
			case GREATER_THAN:
				for (Number effectiveTime : values) {
					query.must(rangeQuery(field).gt(effectiveTime));
				}
				break;
		}
	}

	public Set<Long> findHistoricConcepts(SortedSet<Long> initialConcepts, HistorySupplement historySupplement, BranchCriteria branchCriteria) {
		List<Long> associationTypes = getHistoricAssociationTypes(historySupplement, branchCriteria);

		// Find all active historic associations where the target component id matches one of the initially selected concept.
		// Return the referenced component, these are the inactive concepts with that association.
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class)
						.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
						.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, associationTypes)))
				.withFilter(termsQuery(ReferenceSetMember.Fields.ADDITIONAL_FIELDS_PREFIX + ReferenceSetMember.AssociationFields.TARGET_COMP_ID, initialConcepts))
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.withPageable(LARGE_PAGE);

		Set<Long> conceptIds = new LongOpenHashSet();
		try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchTemplate.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
			stream.forEachRemaining(hit -> conceptIds.add(parseLong(hit.getContent().getReferencedComponentId())));
		}
		return conceptIds;
	}

	private List<Long> getHistoricAssociationTypes(HistorySupplement historySupplement, BranchCriteria branchCriteria) {
		List<Long> associations;

		SExpressionConstraint expressionConstraint = null;
		if (historySupplement.getHistorySubset() != null) {
			expressionConstraint = (SExpressionConstraint) historySupplement.getHistorySubset();
		} else if (historySupplement.getHistoryProfile() == null || historySupplement.getHistoryProfile() == HistoryProfile.MAX) {
			expressionConstraint = historyMaxECL;
		}
		if (expressionConstraint != null) {
			Page<Long> associationsPage = eclQueryService.doSelectConceptIds(expressionConstraint, branchCriteria, false, null, null);
			associations = associationsPage.getContent();
		} else {
			if (historySupplement.getHistoryProfile() == HistoryProfile.MIN) {
				associations = HISTORY_PROFILE_MIN;
			} else {
				associations = HISTORY_PROFILE_MOD;
			}
		}
		return associations;
	}
}
