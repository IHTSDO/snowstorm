package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kaicode.elasticvc.api.BranchCriteria;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.domain.ConceptReference;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.*;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup;
import org.snomed.snowstorm.ecl.ConceptSelectorHelper;
import org.snomed.snowstorm.ecl.ECLContentService;
import org.snomed.snowstorm.ecl.deserializer.ECLModelDeserializer;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.snomed.snowstorm.ecl.domain.filter.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.Long.parseLong;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class SSubExpressionConstraint extends SubExpressionConstraint implements SExpressionConstraint {

	private final Logger logger = LoggerFactory.getLogger(SSubExpressionConstraint.class);

	private int maxTermsCount;

	@SuppressWarnings("unused")
	// For JSON
	private SSubExpressionConstraint() {
		super(null);
	}

	public SSubExpressionConstraint(Operator operator, int maxTermsCount) {
		super(operator);
		this.maxTermsCount = maxTermsCount;
	}

	@Override
	public Optional<Page<Long>> select(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter,
			PageRequest pageRequest, ECLContentService eclContentService, boolean triedCache) {

		if (isUnconstrained()) {
			return Optional.empty();
		}
		return Optional.of(ConceptSelectorHelper.select(this, branchCriteria, stated, conceptIdFilter, pageRequest, eclContentService, triedCache));
	}

	@JsonIgnore
	public boolean isUnconstrained() {
		return wildcard
				&& (operator == null || operator == Operator.descendantorselfof || operator == Operator.ancestororselfof)
				&& !isAnyFiltersOrSupplements();
	}

	@JsonIgnore
	public boolean isAnyFiltersOrSupplements() {
		return isAnyFiltersOrSupplementsExcludingMemberFilters() || memberFilterConstraints != null;
	}

	@JsonIgnore
	public boolean isMemberOfQuery() {
		return operator == Operator.memberOf;
	}

	@JsonIgnore
	public boolean isNestedExpressionConstraintMemberOfQuery() {
		if (nestedExpressionConstraint != null && nestedExpressionConstraint instanceof SSubExpressionConstraint subExpressionConstraint) {
			return subExpressionConstraint.isMemberOfQuery();
		}
		return false;
	}

	@JsonIgnore
	public boolean isAnyFiltersOrSupplementsExcludingMemberFilters() {
		return conceptFilterConstraints != null || descriptionFilterConstraints != null || getHistorySupplement() != null;
	}

	@Override
	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		if (isUnconstrained()) {
			return Optional.empty();
		}
		return Optional.of(ConceptSelectorHelper.select(this, refinementBuilder));
	}

	@Override
	public Set<String> getConceptIds() {
		Set<String> conceptIds = newHashSet();
		if (conceptId != null) {
			conceptIds.add(conceptId);
		}
		if (nestedExpressionConstraint != null) {
			conceptIds.addAll(((SExpressionConstraint) nestedExpressionConstraint).getConceptIds());
		}
		if (conceptFilterConstraints != null) {
			for (ConceptFilterConstraint conceptFilterConstraint : conceptFilterConstraints) {
				collectConceptIds(conceptIds, conceptFilterConstraint.getDefinitionStatusFilters());
				collectConceptIds(conceptIds, conceptFilterConstraint.getModuleFilters());
			}
		}
		if (descriptionFilterConstraints != null) {
			for (DescriptionFilterConstraint descriptionFilterConstraint : descriptionFilterConstraints) {
				for (DescriptionTypeFilter descriptionTypeFilter : orEmpty(descriptionFilterConstraint.getDescriptionTypeFilters())) {
					for (DescriptionType type : orEmpty(descriptionTypeFilter.getTypes())) {
						conceptIds.add(type.getTypeId());
					}
					if (descriptionTypeFilter.getSubExpressionConstraint() != null) {
						conceptIds.addAll(((SSubExpressionConstraint)descriptionTypeFilter.getSubExpressionConstraint()).getConceptIds());
					}
				}
				for (DialectFilter dialectFilter : orEmpty(descriptionFilterConstraint.getDialectFilters())) {
					for (DialectAcceptability dialectAcceptability : dialectFilter.getDialectAcceptabilities()) {
						if (dialectAcceptability.getSubExpressionConstraint() != null) {
							conceptIds.addAll(((SSubExpressionConstraint)dialectAcceptability.getSubExpressionConstraint()).getConceptIds());
						}
						if (dialectAcceptability.getAcceptabilityIdSet() != null) {
							for (ConceptReference conceptReference : dialectAcceptability.getAcceptabilityIdSet()) {
								conceptIds.add(conceptReference.getConceptId());
							}
						}
					}
				}
			}
		}
		if (memberFilterConstraints != null) {
			for (MemberFilterConstraint memberFilterConstraint : memberFilterConstraints) {
				for (MemberFieldFilter memberFieldFilter : orEmpty(memberFilterConstraint.getMemberFieldFilters())) {
					if (memberFieldFilter.getExpressionComparisonOperator() != null) {
						SSubExpressionConstraint subExpressionConstraint = (SSubExpressionConstraint) memberFieldFilter.getSubExpressionConstraint();
						conceptIds.addAll(subExpressionConstraint.getConceptIds());
					}
				}
				collectConceptIds(conceptIds, orEmpty(memberFilterConstraint.getModuleFilters()));
			}
		}
		return conceptIds;
	}

	private void collectConceptIds(Set<String> conceptIds, List<FieldFilter> fieldFilters) {
		for (FieldFilter fieldFilter : orEmpty(fieldFilters)) {
			for (ConceptReference conceptReference : orEmpty(fieldFilter.getConceptReferences())) {
				conceptIds.add(conceptReference.getConceptId());
			}
			SubExpressionConstraint subExpressionConstraint = fieldFilter.getSubExpressionConstraint();
			if (subExpressionConstraint instanceof SSubExpressionConstraint sSubExpressionConstraint) {
				conceptIds.addAll(sSubExpressionConstraint.getConceptIds());
			}
		}
	}

	@Override
	public void setNestedExpressionConstraint(ExpressionConstraint nestedExpressionConstraint) {
		super.setNestedExpressionConstraint(nestedExpressionConstraint);
	}

	private SSubExpressionConstraint cloneWithoutFiltersOrSupplements() {
		SSubExpressionConstraint clone = new SSubExpressionConstraint(operator, maxTermsCount);
		clone.setConceptId(conceptId);
		clone.setTerm(term);
		clone.setWildcard(wildcard);
		clone.setAltIdentifier(altIdentifier);
		clone.setAltIdentifierSchemeAlias(getAltIdentifierSchemeAlias());
		clone.setAltIdentifierCode(getAltIdentifierCode());
		clone.setNestedExpressionConstraint(nestedExpressionConstraint);
		clone.setMemberFieldsToReturn(getMemberFieldsToReturn());
		clone.setReturnAllMemberFields(isReturnAllMemberFields());
		return clone;
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder, Consumer<List<Long>> filteredOrSupplementedContentCallback, boolean triedCache) {
		BoolQuery.Builder query = refinementBuilder.getQueryBuilder();

		if (shouldFetchConceptIds(refinementBuilder)) {
			// Fetching required
			ECLContentService eclContentService = refinementBuilder.getEclContentService();
			BranchCriteria branchCriteria = refinementBuilder.getBranchCriteria();
			boolean stated = refinementBuilder.isStated();

			// Cache results before applying filters, apart from member queries with field filters.
			Collection<Long> prefetchedConceptIds = null;
			if (isNestedExpressionConstraintMemberOfQuery() || (operator == Operator.memberOf && (memberFilterConstraints != null || triedCache))) {
				// If there is a member filter constraint we can assume the results set will be fairly small / not reusable.
				// If there are no filters this
				// Fetch without cache.
 				prefetchedConceptIds = doAddCriteria(refinementBuilder, query);
			}

			if (prefetchedConceptIds == null) {
				if (operator != null && isNestedExpressionConstraintMemberOfQuery()) {
					// No need to fetch nested expression constraint again as nested results applied to query filter already
					// See doAddCriteria method and applyConceptCriteriaWithOperator return null on purpose for this scenario
					return;
				} else {
					// Grab all concept ids using query without filters, should be cached
					SSubExpressionConstraint sSubExpressionConstraint = cloneWithoutFiltersOrSupplements();
					prefetchedConceptIds = eclContentService.fetchAllIdsWithCaching(sSubExpressionConstraint, branchCriteria, stated);
				}
			}

			SortedSet<Long> conceptIdSortedSet = new LongLinkedOpenHashSet(prefetchedConceptIds);
			conceptIdSortedSet = applyFilters(conceptIdSortedSet, eclContentService, branchCriteria, stated);
			query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIdSortedSet));
			filteredOrSupplementedContentCallback.accept(new LongArrayList(conceptIdSortedSet));
		} else {
			doAddCriteria(refinementBuilder, query);
		}
	}

	private boolean shouldFetchConceptIds(RefinementBuilder refinementBuilder) {
		return isAnyFiltersOrSupplements() || ((operator == Operator.memberOf || isNestedExpressionConstraintMemberOfQuery()) &&
				(refinementBuilder.shouldPrefetchMemberOfQueryResults() != null && refinementBuilder.shouldPrefetchMemberOfQueryResults()));
	}

	private SortedSet<Long> applyFilters(SortedSet<Long> conceptIdSortedSet, ECLContentService eclContentService, BranchCriteria branchCriteria, boolean stated) {
		if (!conceptIdSortedSet.isEmpty()) {
			// Apply filter constraints
			if (getConceptFilterConstraints() != null) {
				Set<Long> results = eclContentService.applyConceptFilters(getConceptFilterConstraints(), conceptIdSortedSet, branchCriteria, stated);
				// Need to keep the original order
				conceptIdSortedSet = new LongLinkedOpenHashSet(conceptIdSortedSet.stream().filter(results::contains).toList());
			}
			if (getDescriptionFilterConstraints() != null) {
				// For each filter constraint all sub-filters (term, language, etc) must apply.
				// If multiple options are given within a sub-filter they are conjunction (OR).
				for (DescriptionFilterConstraint descriptionFilter : getDescriptionFilterConstraints()) {
					SortedMap<Long, Long> descriptionToConceptMap = eclContentService.applyDescriptionFilter(conceptIdSortedSet, descriptionFilter, branchCriteria, stated);
					conceptIdSortedSet = new LongLinkedOpenHashSet(descriptionToConceptMap.values());
				}
			}
			// Add history supplement
			if (getHistorySupplement() != null) {
				Set<Long> historicConcepts = eclContentService.findHistoricConcepts(conceptIdSortedSet, getHistorySupplement(), branchCriteria);
				conceptIdSortedSet.addAll(historicConcepts);
			}
		}
		return conceptIdSortedSet;
	}

	/**
	 * Adds criteria to semantic index query.
	 * Has to prefetch conceptIds in some scenarios for example nested constraints or member query.
	 * These are returned by the method to show that further fetches can be avoided.
	 */
	private Collection<Long> doAddCriteria(RefinementBuilder refinementBuilder, BoolQuery.Builder queryBuilder) {
		if (conceptId != null) {
			if (operator != null) {
				return applyConceptCriteriaWithOperator(Collections.singleton(parseLong(conceptId)), operator, refinementBuilder);
			} else {
				queryBuilder.must(termQuery(QueryConcept.Fields.CONCEPT_ID, conceptId));
			}
		} else if (nestedExpressionConstraint != null) {
			Optional<Page<Long>> conceptIdsOptional = ((SExpressionConstraint)nestedExpressionConstraint).select(refinementBuilder);
			if (conceptIdsOptional.isEmpty()) {
				return null;
			}
			List<Long> conceptIds = conceptIdsOptional.get().getContent();
			boolean matchesNothing = false;
			if (conceptIds.isEmpty()) {
				// Attribute type is not a wildcard but empty selection
				// Force query to return nothing
				conceptIds = Collections.singletonList(ConceptSelectorHelper.MISSING_LONG);
				matchesNothing = true;
			}
			BoolQuery.Builder filterQueryBuilder = bool();
			if (operator != null) {
				SubRefinementBuilder filterRefinementBuilder = new SubRefinementBuilder(refinementBuilder, filterQueryBuilder);
				filterRefinementBuilder.setShouldPrefetchMemberOfQueryResults(true);
				Set<Long> results = applyConceptCriteriaWithOperator(conceptIds, operator, filterRefinementBuilder);
				if (results != null && results.size() > maxTermsCount) {
					String message = String.format("Your nested query %s matched too many results (%s) which exceeds the system limit of %s terms. Try narrowing your search.",
							operator.getText() + ((SExpressionConstraint) nestedExpressionConstraint).toEclString(), results.size(), maxTermsCount);
					throw new IllegalArgumentException(message);
				}
				queryBuilder.filter(filterQueryBuilder.build()._toQuery());
				return results;
			} else {
				filterQueryBuilder.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds));
				queryBuilder.filter(filterQueryBuilder.build()._toQuery());
				return matchesNothing ? Collections.emptyList() : conceptIds;
			}

		} else if (operator == Operator.memberOf) {
			// Member of wildcard (any reference set)
			// Can't use concepts lookups here due to no reference set ids specified
			Set<Long> conceptIdsInReferenceSet = refinementBuilder.getEclContentService()
					.findConceptIdsInReferenceSet(null, getMemberFilterConstraints(), refinementBuilder);
			queryBuilder.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIdsInReferenceSet));
			return conceptIdsInReferenceSet;
		} else if (operator == Operator.descendantof || operator == Operator.childof) {
			// Descendant of wildcard / Child of wildcard = anything but root
			queryBuilder.mustNot(termQuery(QueryConcept.Fields.CONCEPT_ID, Concepts.SNOMEDCT_ROOT));
		} else if (operator == Operator.ancestorof || operator == Operator.parentof) {
			// Ancestor of wildcard / Parent of wildcard = all non-leaf concepts
			Collection<Long> conceptsWithDescendants = refinementBuilder.getEclContentService().findRelationshipDestinationIds(
					null, Collections.singletonList(parseLong(Concepts.ISA)), refinementBuilder.getBranchCriteria(), refinementBuilder.isStated());
			queryBuilder.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptsWithDescendants));
		}
		// Else Wildcard! which has no constraints
		// <<!* and >>!* also match everything
		return null;
	}

	private Set<Long> applyConceptCriteriaWithOperator(Collection<Long> conceptIds, Operator operator, RefinementBuilder refinementBuilder) {
		BoolQuery.Builder queryBuilder = refinementBuilder.getQueryBuilder();
		ECLContentService conceptSelector = refinementBuilder.getEclContentService();
		BranchCriteria branchCriteria = refinementBuilder.getBranchCriteria();
		boolean stated = refinementBuilder.isStated();

        switch (operator) {
            case childof ->
                // <!
                    queryBuilder.must(termsQuery(QueryConcept.Fields.PARENTS, conceptIds));
            case childorselfof ->
                // <<!
                    queryBuilder.must(
                            bool(b -> b
                                    .should(termsQuery(QueryConcept.Fields.PARENTS, conceptIds))
                                    .should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds)))
                    );
            case descendantof ->
                // <
                    queryBuilder.must(termsQuery(QueryConcept.Fields.ANCESTORS, conceptIds));
            case descendantorselfof ->
                // <<
                    queryBuilder.must(
                            bool(b -> b
                                    .should(termsQuery(QueryConcept.Fields.ANCESTORS, conceptIds))
                                    .should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds)))
                    );
            case parentof ->
                // >!
                    queryBuilder.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, retrieveAllParents(conceptIds, branchCriteria, stated, conceptSelector)));
            case parentorselfof ->
                // >>!
                    queryBuilder.must(
                            bool(b -> b
                                    .should(termsQuery(QueryConcept.Fields.CONCEPT_ID, retrieveAllParents(conceptIds, branchCriteria, stated, conceptSelector)))
                                    .should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds)))
                    );
            case ancestorof ->
                // >
                    queryBuilder.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, retrieveAllAncestors(conceptIds, branchCriteria, stated, conceptSelector)));
            case ancestororselfof ->
                // >>
                    queryBuilder.must(
                            bool(b -> b
                                    .should(termsQuery(QueryConcept.Fields.CONCEPT_ID, retrieveAllAncestors(conceptIds, branchCriteria, stated, conceptSelector)))
                                    .should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds)))
                    );
            case memberOf -> {
				// ^
				return handleMemberOf(conceptIds, refinementBuilder, queryBuilder, conceptSelector, branchCriteria);
			}
			case top -> {
				if (conceptIds.isEmpty()) {
					queryBuilder.must(termQuery(QueryConcept.Fields.CONCEPT_ID, ConceptSelectorHelper.MISSING_LONG.toString()));
				}
				// >!!
				// Example:
				// - ECL: >!!(1,2,3)
				// - Is equal to ECL: (1,2,3) MINUS < (1,2,3)
				// - The set of concepts, minus concepts that have ancestors in the set
				queryBuilder.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds));
				queryBuilder.mustNot(termsQuery(QueryConcept.Fields.ANCESTORS, conceptIds));
			}
			case bottom -> {
				if (conceptIds.isEmpty()) {
					queryBuilder.must(termQuery(QueryConcept.Fields.CONCEPT_ID, ConceptSelectorHelper.MISSING_LONG.toString()));
				}
				// <!!
				// Example:
				// - ECL: <!!(1,2,3)
				// - Is equal to ECL: (1,2,3) MINUS > (1,2,3)
				// - The set of concepts, minus concepts that have descendants in the set
				queryBuilder.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds));
				queryBuilder.mustNot(termsQuery(QueryConcept.Fields.CONCEPT_ID, retrieveAllAncestors(conceptIds, branchCriteria, stated, conceptSelector)));
			}
            default -> throw new UnsupportedOperationException("Unsupported operator: " + operator);
        }
		return null;
	}


	private Set<Long> handleMemberOf(Collection<Long> conceptIds, RefinementBuilder refinementBuilder, BoolQuery.Builder queryBuilder, ECLContentService conceptSelector, BranchCriteria branchCriteria) {
		// Skip concepts lookup if disabled or use member filter constraints
		if (!conceptSelector.isConceptsLookupEnabled() || hasMemberFilterConstraints()) {
			return findReferencedConceptIds(conceptIds, refinementBuilder, queryBuilder, conceptSelector);
		}
		List<ReferencedConceptsLookup> lookups = conceptSelector.getConceptsLookups(branchCriteria, conceptIds);
		Set<Long> refsetIdsWithLookup = lookups.stream().map(ReferencedConceptsLookup::getRefsetId).map(Long::parseLong).collect(Collectors.toSet());

		if (conceptIds.size() == refsetIdsWithLookup.size()) {
			return handleQueryWithFullLookup(conceptIds, refinementBuilder, queryBuilder, conceptSelector, branchCriteria);
		} else {
			return handleQueryWithPartialOrNoLookup(conceptIds, refsetIdsWithLookup, refinementBuilder, queryBuilder, conceptSelector, branchCriteria);
		}
	}

	private boolean hasMemberFilterConstraints() {
		return getMemberFilterConstraints() != null && !getMemberFilterConstraints().isEmpty();
	}

	private Set<Long> findReferencedConceptIds(Collection<Long> conceptIds, RefinementBuilder refinementBuilder, BoolQuery.Builder queryBuilder, ECLContentService conceptSelector) {
		// Use member service instead of lookups
		Set<Long> filteredIds = conceptSelector.findConceptIdsInReferenceSet(conceptIds, getMemberFilterConstraints(), refinementBuilder);
		queryBuilder.filter(termsQuery(QueryConcept.Fields.CONCEPT_ID, filteredIds));
		return filteredIds;
	}

	private Set<Long> handleQueryWithFullLookup(Collection<Long> conceptIds, RefinementBuilder refinementBuilder, BoolQuery.Builder queryBuilder, ECLContentService conceptSelector, BranchCriteria branchCriteria) {
		if (Boolean.TRUE.equals(refinementBuilder.shouldPrefetchMemberOfQueryResults()) || conceptIds.size() > 1) {
			Set<Long> conceptsFromLookup= conceptSelector.getConceptIdsFromLookup(branchCriteria, conceptIds);
			queryBuilder.filter(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptsFromLookup));
			return conceptsFromLookup;
		} else {
			queryBuilder.filter(conceptSelector.getTermsLookupFilterForMemberOfECL(branchCriteria, conceptIds.iterator().next()));
			return null;
		}
	}

	private Set<Long> handleQueryWithPartialOrNoLookup(Collection<Long> conceptIds, Set<Long> refsetIdsWithLookup, RefinementBuilder refinementBuilder, BoolQuery.Builder queryBuilder, ECLContentService conceptSelector, BranchCriteria branchCriteria) {
		if (refsetIdsWithLookup.isEmpty()) {
			// No Lookups found
			return findReferencedConceptIds(conceptIds, refinementBuilder, queryBuilder, conceptSelector);
		}
		if (Boolean.TRUE.equals(refinementBuilder.shouldPrefetchMemberOfQueryResults())) {
			Set<Long> results = new HashSet<>(conceptSelector.getConceptIdsFromLookup(branchCriteria, refsetIdsWithLookup));
			List<Long> refsetIdsWithoutLookup = new ArrayList<>(conceptIds);
			refsetIdsWithoutLookup.removeAll(refsetIdsWithLookup);
			results.addAll(conceptSelector.findConceptIdsInReferenceSet(refsetIdsWithoutLookup, getMemberFilterConstraints(), refinementBuilder));
			queryBuilder.filter(termsQuery(QueryConcept.Fields.CONCEPT_ID, results));
			return results;
		} else {
			logger.info("Not all reference set ids have lookups, so use the member service to fetch referenced concepts instead");
			return findReferencedConceptIds(conceptIds, refinementBuilder, queryBuilder, conceptSelector);
		}
	}
	private Set<Long> retrieveAllAncestors(Collection<Long> conceptIds, BranchCriteria branchCriteria, boolean stated, ECLContentService eclContentService) {
		return eclContentService.findAncestorIdsAsUnion(branchCriteria, stated, conceptIds);
	}

	private Set<Long> retrieveAllParents(Collection<Long> conceptIds, BranchCriteria branchCriteria, boolean stated, ECLContentService eclContentService) {
		return eclContentService.findParentIdsAsUnion(branchCriteria, stated, conceptIds);
	}

	@Override
	public String toEclString() {
		return toString(new StringBuffer()).toString();
	}

	public StringBuffer toString(StringBuffer buffer) {
		if (operator != null) {
			buffer.append(operator.getText()).append(" ");
			if (operator == Operator.memberOf) {
				boolean returnAllMemberFields = isReturnAllMemberFields();
				List<String> memberFieldsToReturn = getMemberFieldsToReturn();
				if (returnAllMemberFields) {
					buffer.append("[*] ");
				} else if (memberFieldsToReturn != null && !memberFieldsToReturn.isEmpty()) {
					buffer.append("[");
					int a = 0;
					for (String field : memberFieldsToReturn) {
						if (a++ > 0) {
							buffer.append(", ");
						}
						buffer.append(field);
					}
					buffer.append("] ");
				}
			}
		}
		if (conceptId != null) {
			buffer.append(conceptId);
		}
		if (isAltIdentifier()) {
			buffer.append(getAltIdentifierSchemeAlias()).append("#").append(getAltIdentifierCode());
		}
		if (term != null) {
			buffer.append(" |").append(term).append("|");
		}
		if (wildcard) {
			buffer.append("*");
		}
		if (nestedExpressionConstraint != null) {
			buffer.append("( ");
			ECLModelDeserializer.expressionConstraintToString(nestedExpressionConstraint, buffer);
			buffer.append(" )");
		}
		for (ConceptFilterConstraint conceptFilterConstraint : orEmpty(conceptFilterConstraints)) {
			((SConceptFilterConstraint)conceptFilterConstraint).toString(buffer);
		}
		for (DescriptionFilterConstraint descriptionFilterConstraint : orEmpty(descriptionFilterConstraints)) {
			((SDescriptionFilterConstraint) descriptionFilterConstraint).toString(buffer);
		}
		for (MemberFilterConstraint memberFilterConstraint : orEmpty(memberFilterConstraints)) {
			((SMemberFilterConstraint) memberFilterConstraint).toString(buffer);
		}
		if (getHistorySupplement() != null) {
			((SHistorySupplement)getHistorySupplement()).toString(buffer);
		}
		return buffer;
	}
}
