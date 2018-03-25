package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class SSubExpressionConstraint extends SubExpressionConstraint implements SExpressionConstraint {

	public SSubExpressionConstraint(Operator operator) {
		super(operator);
	}

	@Override
	public Optional<Page<Long>> select(String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		if (wildcard && Operator.memberOf != operator) {
			return Optional.empty();
		}
		return SExpressionConstraintHelper.select(this, path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService);
	}

	@Override
	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		if (wildcard && Operator.memberOf != operator) {
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
				applyConceptCriteriaWithOperator(conceptId, operator, refinementBuilder);
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
				for (Long conceptId : conceptIds) {
					applyConceptCriteriaWithOperator(conceptId.toString(), operator, filterRefinementBuilder);
				}
			} else {
				filterQuery.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, conceptIds));
			}
		} else if (operator == Operator.memberOf) {
			// Member of any reference set
			query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, refinementBuilder.getQueryService().retrieveConceptsInReferenceSet(refinementBuilder.getBranchCriteria(), null)));
		}
		// Else Wildcard! which has no constraints
	}

	private void applyConceptCriteriaWithOperator(String conceptId, Operator operator, RefinementBuilder refinementBuilder) {
		BoolQueryBuilder query = refinementBuilder.getQuery();
		QueryService queryService = refinementBuilder.getQueryService();
		QueryBuilder branchCriteria = refinementBuilder.getBranchCriteria();
		String path = refinementBuilder.getPath();
		boolean stated = refinementBuilder.isStated();

		switch (operator) {
			case childof:
				query.must(termQuery(QueryConcept.PARENTS_FIELD, conceptId));
				break;
			case descendantorselfof:
				// <<
				query.must(
						boolQuery()
								.should(termQuery(QueryConcept.ANCESTORS_FIELD, conceptId))
								.should(termQuery(QueryConcept.CONCEPT_ID_FIELD, conceptId))
				);
				break;
			case descendantof:
				// <
				query.must(termQuery(QueryConcept.ANCESTORS_FIELD, conceptId));
				break;
			case parentof:
				Set<Long> parents = queryService.retrieveParents(branchCriteria, path, stated, conceptId);
				query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, parents));
				break;
			case ancestororselfof:
				query.must(
						boolQuery()
								.should(termsQuery(QueryConcept.CONCEPT_ID_FIELD, queryService.retrieveAncestors(branchCriteria, path, stated, conceptId)))
								.should(termQuery(QueryConcept.CONCEPT_ID_FIELD, conceptId))
				);
				break;
			case ancestorof:
				// > x
				query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, queryService.retrieveAncestors(branchCriteria, path, stated, conceptId)));
				break;
			case memberOf:
				// ^
				query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, queryService.retrieveConceptsInReferenceSet(branchCriteria, conceptId)));
				break;
		}
	}

}
