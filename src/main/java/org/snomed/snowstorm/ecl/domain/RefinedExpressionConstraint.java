package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.Collection;
import java.util.List;

public class RefinedExpressionConstraint extends ExpressionConstraint {

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
	public String toString() {
		return "RefinedExpressionConstraint{" +
				"subexpressionConstraint=" + subexpressionConstraint +
				", eclRefinement=" + eclRefinement +
				'}';
	}
}
