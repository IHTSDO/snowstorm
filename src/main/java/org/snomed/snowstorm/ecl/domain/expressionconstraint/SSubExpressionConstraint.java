package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kaicode.elasticvc.api.BranchCriteria;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.snomed.langauges.ecl.domain.ConceptReference;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.*;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.ecl.ConceptSelectorHelper;
import org.snomed.snowstorm.ecl.ECLContentService;
import org.snomed.snowstorm.ecl.deserializer.ECLModelDeserializer;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.snomed.snowstorm.ecl.domain.filter.SConceptFilterConstraint;
import org.snomed.snowstorm.ecl.domain.filter.SDescriptionFilterConstraint;
import org.snomed.snowstorm.ecl.domain.filter.SHistorySupplement;
import org.snomed.snowstorm.ecl.domain.filter.SMemberFilterConstraint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;
import java.util.function.Consumer;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class SSubExpressionConstraint extends SubExpressionConstraint implements SExpressionConstraint {

	@SuppressWarnings("unused")
	// For JSON
	private SSubExpressionConstraint() {
		this(null);
	}

	public SSubExpressionConstraint(Operator operator) {
		super(operator);
	}

	@Override
	public Optional<Page<Long>> select(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter,
			PageRequest pageRequest, ECLContentService eclContentService) {

		if (isUnconstrained()) {
			return Optional.empty();
		}
		return Optional.of(ConceptSelectorHelper.select(this, branchCriteria, stated, conceptIdFilter, pageRequest, eclContentService));
	}

	@JsonIgnore
	public boolean isUnconstrained() {
		return wildcard
				&& (operator == null || operator == Operator.descendantorselfof || operator == Operator.ancestororselfof)
				&& !isAnyFiltersOrSupplements();
	}

	@JsonIgnore
	public boolean isAnyFiltersOrSupplements() {
		return conceptFilterConstraints != null || descriptionFilterConstraints != null || memberFilterConstraints != null || getHistorySupplement() != null;
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
		return conceptIds;
	}

	private void collectConceptIds(Set<String> conceptIds, List<FieldFilter> fieldFilters) {
		for (FieldFilter fieldFilter : orEmpty(fieldFilters)) {
			for (ConceptReference conceptReference : orEmpty(fieldFilter.getConceptReferences())) {
				conceptIds.add(conceptReference.getConceptId());
			}
			SubExpressionConstraint subExpressionConstraint = fieldFilter.getSubExpressionConstraint();
			if (subExpressionConstraint instanceof SSubExpressionConstraint) {
				conceptIds.addAll(((SSubExpressionConstraint) subExpressionConstraint).getConceptIds());
			}
		}
	}

	@Override
	public void setNestedExpressionConstraint(ExpressionConstraint nestedExpressionConstraint) {
		if (nestedExpressionConstraint != null && operator == Operator.memberOf) {
			throw new UnsupportedOperationException("MemberOf nested expression constraint is not supported.");
		}
		super.setNestedExpressionConstraint(nestedExpressionConstraint);
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder, Consumer<List<Long>> filteredOrSupplementedContentCallback) {
		BoolQueryBuilder query = refinementBuilder.getQuery();
		doAddCriteria(refinementBuilder, query);

		if (isAnyFiltersOrSupplements()) {
			//
			// Run content selection using filters and apply further conceptId constraints
			//

			ECLContentService eclContentService = refinementBuilder.getEclContentService();
			BranchCriteria branchCriteria = refinementBuilder.getBranchCriteria();
			boolean stated = refinementBuilder.isStated();

			// Grab all concept ids, this call is cacheable
			SortedSet<Long> conceptIdSortedSet =
					new LongLinkedOpenHashSet(ConceptSelectorHelper.fetchIds(query, null, refinementBuilder, null).getContent());

			// Apply filter constraints
			if (getConceptFilterConstraints() != null) {
				conceptIdSortedSet = new LongLinkedOpenHashSet(eclContentService.applyConceptFilters(getConceptFilterConstraints(), conceptIdSortedSet, branchCriteria, stated));
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

			query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIdSortedSet));
			filteredOrSupplementedContentCallback.accept(new LongArrayList(conceptIdSortedSet));
		}
	}

	private void doAddCriteria(RefinementBuilder refinementBuilder, BoolQueryBuilder query) {
		if (conceptId != null) {
			if (operator != null) {
				applyConceptCriteriaWithOperator(Collections.singleton(parseLong(conceptId)), operator, refinementBuilder);
			} else {
				query.must(QueryBuilders.termQuery(QueryConcept.Fields.CONCEPT_ID, conceptId));
			}
		} else if (nestedExpressionConstraint != null) {
			Optional<Page<Long>> conceptIdsOptional = ((SExpressionConstraint)nestedExpressionConstraint).select(refinementBuilder);
			if (conceptIdsOptional.isEmpty()) {
				return;
			}
			List<Long> conceptIds = conceptIdsOptional.get().getContent();
			if (conceptIds.isEmpty()) {
				// Attribute type is not a wildcard but empty selection
				// Force query to return nothing
				conceptIds = Collections.singletonList(ConceptSelectorHelper.MISSING_LONG);
			}
			BoolQueryBuilder filterQuery = boolQuery();
			query.filter(filterQuery);
			if (operator != null) {
				SubRefinementBuilder filterRefinementBuilder = new SubRefinementBuilder(refinementBuilder, filterQuery);
				applyConceptCriteriaWithOperator(conceptIds, operator, filterRefinementBuilder);
			} else {
				filterQuery.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds));
			}
		} else if (operator == Operator.memberOf) {
			// Member of wildcard (any reference set)
			Set<Long> conceptIdsInReferenceSet = refinementBuilder.getEclContentService()
					.findConceptIdsInReferenceSet(null, getMemberFilterConstraints(), refinementBuilder.getBranchCriteria());
			query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIdsInReferenceSet));
		} else if (operator == Operator.descendantof || operator == Operator.childof) {
			// Descendant of wildcard / Child of wildcard = anything but root
			query.mustNot(termQuery(QueryConcept.Fields.CONCEPT_ID, Concepts.SNOMEDCT_ROOT));
		} else if (operator == Operator.ancestorof || operator == Operator.parentof) {
			// Ancestor of wildcard / Parent of wildcard = all non-leaf concepts
			Collection<Long> conceptsWithDescendants = refinementBuilder.getEclContentService().findRelationshipDestinationIds(
					null, Collections.singletonList(parseLong(Concepts.ISA)), refinementBuilder.getBranchCriteria(), refinementBuilder.isStated());
			query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptsWithDescendants));
		}
		// Else Wildcard! which has no constraints
		// <<!* and >>!* also match everything
	}

	private void applyConceptCriteriaWithOperator(Collection<Long> conceptIds, Operator operator, RefinementBuilder refinementBuilder) {
		BoolQueryBuilder query = refinementBuilder.getQuery();
		ECLContentService conceptSelector = refinementBuilder.getEclContentService();
		BranchCriteria branchCriteria = refinementBuilder.getBranchCriteria();
		boolean stated = refinementBuilder.isStated();

		switch (operator) {
			case childof:
				// <!
				query.must(termsQuery(QueryConcept.Fields.PARENTS, conceptIds));
				break;
			case childorselfof:
				// <<!
				query.must(
						boolQuery()
								.should(termsQuery(QueryConcept.Fields.PARENTS, conceptIds))
								.should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds))
				);
				break;
			case descendantorselfof:
				// <<
				query.must(
						boolQuery()
								.should(termsQuery(QueryConcept.Fields.ANCESTORS, conceptIds))
								.should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds))
				);
				break;
			case descendantof:
				// <
				query.must(termsQuery(QueryConcept.Fields.ANCESTORS, conceptIds));
				break;
			case parentof:
				// >!
				for (Long conceptId : conceptIds) {
					Set<Long> parents = conceptSelector.findParentIds(branchCriteria, stated, Collections.singleton(conceptId));
					query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, parents));
				}
				break;
			case parentorselfof:
				// >>!
				BoolQueryBuilder parentsQuery = boolQuery();
				for (Long conceptId : conceptIds) {
					Set<Long> parents = conceptSelector.findParentIds(branchCriteria, stated, Collections.singleton(conceptId));
					parentsQuery.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, parents));
				}
				query.must(
						boolQuery()
								.should(parentsQuery)
								.should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds))
				);
				break;
			case ancestororselfof:
				Set<Long> allAncestors = retrieveAllAncestors(conceptIds, branchCriteria, stated, conceptSelector);
				query.must(
						boolQuery()
								.should(termsQuery(QueryConcept.Fields.CONCEPT_ID, allAncestors))
								.should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds))
				);
				break;
			case ancestorof:
				// > x
				Set<Long> allAncestors2 = retrieveAllAncestors(conceptIds, branchCriteria, stated, conceptSelector);
				query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, allAncestors2));
				break;
			case memberOf:
				// ^
				Set<Long> conceptIdsInReferenceSet = conceptSelector.findConceptIdsInReferenceSet(conceptId, getMemberFilterConstraints(), branchCriteria);
				query.filter(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIdsInReferenceSet));
				break;
		}
	}

	private Set<Long> retrieveAllAncestors(Collection<Long> conceptIds, BranchCriteria branchCriteria, boolean stated, ECLContentService eclContentService) {
		return eclContentService.findAncestorIdsAsUnion(branchCriteria, stated, conceptIds);
	}

	@Override
	public String toEclString() {
		return toString(new StringBuffer()).toString();
	}

	public StringBuffer toString(StringBuffer buffer) {
		if (operator != null) {
			buffer.append(operator.getText()).append(" ");
		}
		if (conceptId != null) {
			buffer.append(conceptId);
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
			System.out.println(conceptFilterConstraint.getClass().toString());
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
