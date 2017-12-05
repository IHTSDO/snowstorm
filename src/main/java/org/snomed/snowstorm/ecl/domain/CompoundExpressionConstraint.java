package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.Collection;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class CompoundExpressionConstraint implements ExpressionConstraint {

	private List<SubExpressionConstraint> conjunctionExpressionConstraints;
	private List<SubExpressionConstraint> disjunctionExpressionConstraints;

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		if (conjunctionExpressionConstraints != null) {
			for (SubExpressionConstraint conjunctionExpressionConstraint : conjunctionExpressionConstraints) {
				conjunctionExpressionConstraint.addCriteria(query, path, branchCriteria, stated, queryService);
			}
		}
		if (disjunctionExpressionConstraints != null) {
			BoolQueryBuilder shouldQueries = boolQuery();
			query.must(shouldQueries);
			for (SubExpressionConstraint disjunctionExpressionConstraint : disjunctionExpressionConstraints) {
				BoolQueryBuilder shouldQuery = boolQuery();
				shouldQueries.should(shouldQuery);
				disjunctionExpressionConstraint.addCriteria(shouldQuery, path, branchCriteria, stated, queryService);
			}
		}
	}

	@Override
	public List<Long> select(String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, QueryService queryService) {
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);
		addCriteria(query, path, branchCriteria, stated, queryService);
		return ConceptSelectorHelper.fetch(query, conceptIdFilter, queryService);
	}

	public void setConjunctionExpressionConstraints(List<SubExpressionConstraint> conjunctionExpressionConstraints) {
		this.conjunctionExpressionConstraints = conjunctionExpressionConstraints;
	}

	public void setDisjunctionExpressionConstraints(List<SubExpressionConstraint> disjunctionExpressionConstraints) {
		this.disjunctionExpressionConstraints = disjunctionExpressionConstraints;
	}

}
