package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

public class SSubExpressionConstraint extends SubExpressionConstraint implements SExpressionConstraint {

	public SSubExpressionConstraint(Operator operator) {
		super(operator);
	}

	@Override
	public Optional<Page<Long>> select(String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		if (isUnconstrained()) {
			return Optional.empty();
		}
		return SExpressionConstraintHelper.select(this, path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService);
	}

	private boolean isUnconstrained() {
		return wildcard && (operator == null || operator == Operator.descendantorselfof || operator == Operator.ancestororselfof);
	}

	@Override
	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		if (isUnconstrained()) {
			return Optional.empty();
		}
		return SExpressionConstraintHelper.select(this, refinementBuilder);
	}

	@Override
	public void setNestedExpressionConstraint(ExpressionConstraint nestedExpressionConstraint) {
		if (operator == Operator.memberOf) {
			throw new UnsupportedOperationException("MemberOf nested expression constraint is not supported.");
		}
		super.setNestedExpressionConstraint(nestedExpressionConstraint);
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		BoolQueryBuilder query = refinementBuilder.getQuery();
		if (conceptId != null) {
			if (operator != null) {
				applyConceptCriteriaWithOperator(Collections.singleton(parseLong(conceptId)), operator, refinementBuilder);
			} else {
				query.must(QueryBuilders.termQuery(QueryConcept.CONCEPT_ID_FIELD, conceptId));
			}
		} else if (nestedExpressionConstraint != null) {
			Optional<Page<Long>> conceptIdsOptional = ((SExpressionConstraint)nestedExpressionConstraint).select(refinementBuilder);
			if (!conceptIdsOptional.isPresent()) {
				return;
			}
			List<Long> conceptIds = conceptIdsOptional.get().getContent();
			if (conceptIds.isEmpty()) {
				// Attribute type is not a wildcard but empty selection
				// Force query to return nothing
				conceptIds = Collections.singletonList(SExpressionConstraintHelper.MISSING_LONG);
			}
			BoolQueryBuilder filterQuery = boolQuery();
			query.filter(filterQuery);
			if (operator != null) {
				SubRefinementBuilder filterRefinementBuilder = new SubRefinementBuilder(refinementBuilder, filterQuery);
				applyConceptCriteriaWithOperator(conceptIds, operator, filterRefinementBuilder);
			} else {
				filterQuery.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, conceptIds));
			}
		} else if (operator == Operator.memberOf) {
			// Member of wildcard (any reference set)
			query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, refinementBuilder.getQueryService().retrieveConceptsInReferenceSet(refinementBuilder.getBranchCriteria(), null)));
		} else if (operator == Operator.descendantof || operator == Operator.childof) {
			// Descendant of wildcard / Child of wildcard = anything but root
			query.mustNot(termQuery(QueryConcept.CONCEPT_ID_FIELD, Concepts.SNOMEDCT_ROOT));
		} else if (operator == Operator.ancestorof || operator == Operator.parentof) {
			// Ancestor of wildcard / Parent of wildcard = all non-leaf concepts
			Collection<Long> conceptsWithDescendants = refinementBuilder.getQueryService().retrieveRelationshipDestinations(
					null, Collections.singletonList(parseLong(Concepts.ISA)), refinementBuilder.getBranchCriteria(), refinementBuilder.isStated());
			query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, conceptsWithDescendants));
		}
		// Else Wildcard! which has no constraints
	}

	private void applyConceptCriteriaWithOperator(Collection<Long> conceptIds, Operator operator, RefinementBuilder refinementBuilder) {
		BoolQueryBuilder query = refinementBuilder.getQuery();
		QueryService queryService = refinementBuilder.getQueryService();
		QueryBuilder branchCriteria = refinementBuilder.getBranchCriteria();
		String path = refinementBuilder.getPath();
		boolean stated = refinementBuilder.isStated();

		switch (operator) {
			case childof:
				query.must(termsQuery(QueryConcept.PARENTS_FIELD, conceptIds));
				break;
			case descendantorselfof:
				// <<
				query.must(
						boolQuery()
								.should(termsQuery(QueryConcept.ANCESTORS_FIELD, conceptIds))
								.should(termsQuery(QueryConcept.CONCEPT_ID_FIELD, conceptIds))
				);
				break;
			case descendantof:
				// <
				query.must(termsQuery(QueryConcept.ANCESTORS_FIELD, conceptIds));
				break;
			case parentof:
				for (Long conceptId : conceptIds) {
					Set<Long> parents = queryService.retrieveParents(branchCriteria, stated, conceptId.toString());
					query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, parents));
				}
				break;
			case ancestororselfof:
				Set<Long> allAncestors = retrieveAllAncestors(conceptIds, branchCriteria, path, stated, queryService);
				query.must(
						boolQuery()
								.should(termsQuery(QueryConcept.CONCEPT_ID_FIELD, allAncestors))
								.should(termsQuery(QueryConcept.CONCEPT_ID_FIELD, conceptIds))
				);
				break;
			case ancestorof:
				// > x
				Set<Long> allAncestors2 = retrieveAllAncestors(conceptIds, branchCriteria, path, stated, queryService);
				query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, allAncestors2));
				break;
			case memberOf:
				// ^
				query.filter(termsQuery(QueryConcept.CONCEPT_ID_FIELD, queryService.retrieveConceptsInReferenceSet(branchCriteria, conceptId)));
				break;
		}
	}

	private Set<Long> retrieveAllAncestors(Collection<Long> conceptIds, QueryBuilder branchCriteria, String path, boolean stated, QueryService queryService) {
		Set<Long> allAncestors = new LongArraySet();
		for (Long conceptId : conceptIds) {
			allAncestors.addAll(queryService.retrieveAncestors(branchCriteria, path, stated, conceptId.toString()));
		}
		return allAncestors;
	}

}
