package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.QueryConcept;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class SubExpressionConstraint implements ExpressionConstraint {

	private final Operator operator;

	private String conceptId;
	private boolean wildcard;
	private ExpressionConstraint nestedExpressionConstraint;

	public SubExpressionConstraint(Operator operator) {
		this.operator = operator;
	}

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		if (conceptId != null) {
			if (operator != null) {
				applyConceptCriteriaWithOperator(conceptId, operator, query, path, branchCriteria, stated, queryService);
			} else {
				query.must(termQuery(QueryConcept.CONCEPT_ID_FIELD, conceptId));
			}
		} else if (nestedExpressionConstraint != null) {
			Collection<Long> conceptIds = nestedExpressionConstraint.select(path, branchCriteria, stated, queryService);
			if (!conceptIds.isEmpty()) {
				conceptIds.add(ExpressionConstraint.MISSING_LONG);
			}
			BoolQueryBuilder filterQuery = boolQuery();
			query.filter(filterQuery);
			if (operator != null) {
				for (Long conceptId : conceptIds) {
					applyConceptCriteriaWithOperator(conceptId.toString(), operator, filterQuery, path, branchCriteria, stated, queryService);
				}
			} else {
				filterQuery.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, conceptIds));
			}
		}
		// Else Wildcard! which has no constraints
	}

	@Override
	public List<Long> select(String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		return select(path, branchCriteria, stated, queryService, null);
	}

	@Override
	public List<Long> select(String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService, List<Long> conceptIdFilter) {
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);
		addCriteria(query, path, branchCriteria, stated, queryService);
		// TODO: Avoid this fetch in the case that we selecting a single known concept
		return ConceptSelectorHelper.fetch(query, conceptIdFilter, queryService);
	}

	private void applyConceptCriteriaWithOperator(String conceptId, Operator operator, BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
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

}
