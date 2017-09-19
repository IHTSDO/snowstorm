package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

import java.util.List;

public class RefinedExpressionConstraint implements ExpressionConstraint {

	private SubExpressionConstraint subexpressionConstraint;
	private EclRefinement eclRefinement;

	public RefinedExpressionConstraint(SubExpressionConstraint subExpressionConstraint, EclRefinement eclRefinement) {
		this.subexpressionConstraint = subExpressionConstraint;
		this.eclRefinement = eclRefinement;
	}

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		subexpressionConstraint.addCriteria(query, path, branchCriteria, stated, queryService);
		eclRefinement.addCriteria(query, path, branchCriteria, stated, queryService);
	}

	@Override
	public List<Long> select(String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);
		addCriteria(query, path, branchCriteria, stated, queryService);
		return ConceptSelectorHelper.fetch(query, queryService);
	}
}
