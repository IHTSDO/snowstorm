package org.snomed.snowstorm.ecl;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import io.kaicode.elasticvc.api.BranchCriteria;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

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
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

/**
 * Service responsible for selecting SNOMED CT content on behalf of the ECL implementation.
 * Having a single class responsible for this gives good separation of concern and eases refactoring/maintenance.
 */
@Service
public class ECLContentService {

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

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

	public Page<QueryConcept> queryForPage(NativeQuery searchQuery) {
		searchQuery.setTrackTotalHits(true);
		Pageable pageable = searchQuery.getPageable();
		SearchHits<QueryConcept> searchHits = elasticsearchOperations.search(searchQuery, QueryConcept.class);
		return PageHelper.toSearchAfterPage(searchHits, pageable);
	}

	public SearchHitsIterator<QueryConcept> streamQueryResults(NativeQuery searchQuery) {
		return elasticsearchOperations.searchForStream(searchQuery, QueryConcept.class);
	}

	public SearchAfterPage<ReferenceSetMember> findReferenceSetMembers(Collection<Long> refsets, List<MemberFilterConstraint> memberFilterConstraints,
			List<String> memberFieldsToReturn, Collection<Long> conceptIdFilter, boolean stated, BranchCriteria branchCriteria, PageRequest pageRequest,
			ECLContentService eclContentService) {

		BoolQuery.Builder masterMemberQuery = buildECLMemberQuery(memberFilterConstraints, stated, branchCriteria);
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
		BoolQuery.Builder masterMemberQuery = buildECLMemberQuery(memberFilterConstraints, refinementBuilder.isStated(), refinementBuilder.getBranchCriteria());
		return memberService.findConceptsInReferenceSet(referenceSetIds, memberFilterConstraints, refinementBuilder, masterMemberQuery);
	}

	private BoolQuery.Builder buildECLMemberQuery(List<MemberFilterConstraint> memberFilterConstraints, boolean stated, BranchCriteria branchCriteria) {
		BoolQuery.Builder masterMemberQuery = bool().must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class));

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

		BoolQuery.Builder boolQueryBuilder = bool()
				.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
				.must(termQuery(QueryConcept.Fields.STATED, stated));

		if (attributeTypeIds != null) {
			BoolQuery.Builder shoulds = bool();
			for (Long attributeTypeId : attributeTypeIds) {
				if (!attributeTypeId.equals(Concepts.IS_A_LONG)) {
					shoulds.should(existsQuery(QueryConcept.Fields.ATTR + "." + attributeTypeId));
				}
			}
			boolQueryBuilder.must(shoulds.build()._toQuery());
		}

		if (sourceConceptIds != null) {
			boolQueryBuilder.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, sourceConceptIds));
		}

		NativeQuery query = new NativeQueryBuilder()
				.withQuery(boolQueryBuilder.build()._toQuery())
				.withPageable(LARGE_PAGE)
				.build();

		Set<Long> destinationIds = new LongArraySet();
		try (SearchHitsIterator<QueryConcept> stream = elasticsearchOperations.searchForStream(query, QueryConcept.class)) {
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

	public Set<Long> findAncestorIdsAsUnion(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIds) {
		return queryService.findAncestorIdsAsUnion(branchCriteria, stated, conceptIds);
	}

	public Set<Long> findParentIdsAsUnion(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIds) {
		return queryService.findParentIdsAsUnion(branchCriteria, stated, conceptIds);
	}

	public Set<Long> applyConceptFilters(List<ConceptFilterConstraint> conceptFilters, Set<Long> conceptIdsToFilter, BranchCriteria branchCriteria, boolean stated) {

		BoolQuery.Builder superQueryBuilder = bool().must(branchCriteria.getEntityBranchCriteria(Concept.class));
		for (ConceptFilterConstraint conceptFilter : conceptFilters) {
			BoolQuery.Builder conceptFilterBuilder = bool();

			// Active filter
			applyActiveFilters(conceptFilter.getActiveFilters(), conceptFilterBuilder);

			// Definition status filter
			applyFieldFilters(conceptFilter.getDefinitionStatusFilters(), conceptFilterBuilder, branchCriteria, stated, "<<900000000000444006 |Definition status (core metadata concept)|");

			// Module filter
			applyFieldFilters(conceptFilter.getModuleFilters(), conceptFilterBuilder, branchCriteria, stated, "<<900000000000443000 |Module (core metadata concept)|");

			// EffectiveTimeFilter
			applyEffectiveTimeFilters(conceptFilter.getEffectiveTimeFilters(), conceptFilterBuilder);

			superQueryBuilder.must(conceptFilterBuilder.build()._toQuery());
		}

		NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(superQueryBuilder.build()._toQuery())
				.withFilter(termsQuery(Concept.Fields.CONCEPT_ID, conceptIdsToFilter))
				.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
				.withPageable(LARGE_PAGE);
		Set<Long> conceptIds = new LongOpenHashSet();
		try (SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(queryBuilder.build(), Concept.class)) {
			stream.forEachRemaining(hit -> conceptIds.add(hit.getContent().getConceptIdAsLong()));
		}

		return conceptIds;
	}

	public SortedMap<Long, Long> applyDescriptionFilter(Collection<Long> conceptIds, DescriptionFilterConstraint descriptionFilter, BranchCriteria branchCriteria, boolean stated) {

		BoolQuery.Builder masterDescriptionQuery = bool();

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

	private void applyFieldFilters(List<FieldFilter> fieldFilters, BoolQuery.Builder filterQuery, BranchCriteria branchCriteria, boolean stated, String eclContentFilter) {
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
				Query termsQuery = termsQuery(field, values);
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

	private void applyActiveFilters(List<ActiveFilter> activeFilters, BoolQuery.Builder componentFilterQuery) {
		activeFilters = orEmpty(activeFilters);
		if (activeFilters.isEmpty()) {
			componentFilterQuery.must(termQuery(SnomedComponent.Fields.ACTIVE, true));
		} else {
			for (ActiveFilter activeFilter : activeFilters) {
				componentFilterQuery.must(termQuery(SnomedComponent.Fields.ACTIVE, activeFilter.isActive()));
			}
		}
	}

	private void applyEffectiveTimeFilters(List<EffectiveTimeFilter> effectiveTimeFilters, BoolQuery.Builder componentFilterQuery) {
		for (EffectiveTimeFilter effectiveTimeFilter : orEmpty(effectiveTimeFilters)) {
			NumericComparisonOperator operator = effectiveTimeFilter.getOperator();
			List<Integer> effectiveTimes = effectiveTimeFilter.getEffectiveTime();
			BoolQuery.Builder boolBuilder = bool();
			String effectiveTimeField = SnomedComponent.Fields.EFFECTIVE_TIME;
			addNumericConstraint(operator, effectiveTimeField, effectiveTimes, boolBuilder);
			componentFilterQuery.must(boolBuilder.build()._toQuery());
		}
	}

	public static void addNumericConstraint(NumericComparisonOperator operator, String field, Collection<? extends Number> values, BoolQuery.Builder query) {
        switch (operator) {
            case EQUAL -> query.must(termsQuery(field, values));
            case NOT_EQUAL -> query.mustNot(termsQuery(field, values));
            case LESS_THAN_OR_EQUAL -> {
                for (Number effectiveTime : values) {
                    query.must(range().field(field).lte(JsonData.of(effectiveTime)).build()._toQuery());
                }
            }
            case LESS_THAN -> {
                for (Number effectiveTime : values) {
                    query.must(range().field(field).lt(JsonData.of(effectiveTime)).build()._toQuery());
                }
            }
            case GREATER_THAN_OR_EQUAL -> {
                for (Number effectiveTime : values) {
                    query.must(range().field(field).gte(JsonData.of(effectiveTime)).build()._toQuery());
                }
            }
            case GREATER_THAN -> {
                for (Number effectiveTime : values) {
                    query.must(range().field(field).gt(JsonData.of(effectiveTime)).build()._toQuery());
                }
            }
        }
	}

	public Set<Long> findHistoricConcepts(SortedSet<Long> initialConcepts, HistorySupplement historySupplement, BranchCriteria branchCriteria) {
		List<Long> associationTypes = getHistoricAssociationTypes(historySupplement, branchCriteria);

		// Find all active historic associations where the target component id matches one of the initially selected concept.
		// Return the referenced component, these are the inactive concepts with that association.
		NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
						.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, associationTypes))))
				.withFilter(termsQuery(ReferenceSetMember.Fields.ADDITIONAL_FIELDS_PREFIX + ReferenceSetMember.AssociationFields.TARGET_COMP_ID, initialConcepts))
				.withSourceFilter(new FetchSourceFilter(new String[]{ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID}, null))
				.withPageable(LARGE_PAGE);

		Set<Long> conceptIds = new LongOpenHashSet();
		try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchOperations.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
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
