package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.snomed.snowstorm.ecl.domain.refinement.Operator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class SubExpressionConstraint extends ExpressionConstraint {

	private final Operator operator;
	private String conceptId;
	private boolean wildcard;
	private ExpressionConstraint nestedExpressionConstraint;

	public SubExpressionConstraint(Operator operator) {
		this.operator = operator;
	}

	@Override
	public boolean isWildcard() {
		return wildcard && Operator.memberOf != operator;
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
			Optional<Page<Long>> conceptIdsOptional = nestedExpressionConstraint.select(refinementBuilder);
			if (!conceptIdsOptional.isPresent()) {
				return;
			}
			List<Long> conceptIds = conceptIdsOptional.get().getContent();
			if (conceptIds.isEmpty()) {
				// Attribute type is not a wildcard but empty selection
				// Force query to return nothing
				conceptIds = Collections.singletonList(ExpressionConstraint.MISSING_LONG);
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

	@Override
	public Optional<Page<Long>> select(String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		if (isWildcard()) {
			return Optional.empty();
		}
		return super.select(path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService);
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

	public void wildcard() {
		this.wildcard = true;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setNestedExpressionConstraint(ExpressionConstraint nestedExpressionConstraint) {
		this.nestedExpressionConstraint = nestedExpressionConstraint;
	}

	@Override
	public String toString() {
		return "SubExpressionConstraint{" +
				"operator=" + operator +
				", conceptId='" + conceptId + '\'' +
				", wildcard=" + wildcard +
				", nestedExpressionConstraint=" + nestedExpressionConstraint +
				'}';
	}
}
