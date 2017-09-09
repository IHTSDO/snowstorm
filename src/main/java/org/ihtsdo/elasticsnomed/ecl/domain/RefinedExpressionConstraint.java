package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;

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

		EclAttribute attribute = eclRefinement.getSubRefinement().getEclAttributeSet().getSubAttributeSet().getAttribute();
		attribute.addCriteria(query, path, branchCriteria, stated, queryService);
	}

	@Override
	public Set<Long> select(String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		return select(path, branchCriteria, stated, queryService, null, null);
	}

	@Override
	public Set<Long> select(String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService, List<Long> conceptIdFilter, PageRequest pageRequest) {
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);
		addCriteria(query, path, branchCriteria, stated, queryService);
		return ConceptSelectorHelper.fetch(query, conceptIdFilter, queryService, pageRequest);
	}
}
