package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;

import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class CompoundExpressionConstraint extends ExpressionConstraint {

	private List<SubExpressionConstraint> conjunctionExpressionConstraints;
	private List<SubExpressionConstraint> disjunctionExpressionConstraints;
	private SubExpressionConstraint exclusionExpressionConstraint;

	@Override
	protected boolean isWildcard() {
		return false;
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		if (conjunctionExpressionConstraints != null) {
			for (SubExpressionConstraint conjunctionExpressionConstraint : conjunctionExpressionConstraints) {
				conjunctionExpressionConstraint.addCriteria(refinementBuilder);
			}
		}
		if (disjunctionExpressionConstraints != null) {
			BoolQueryBuilder shouldQueries = boolQuery();
			refinementBuilder.getQuery().must(shouldQueries);
			for (SubExpressionConstraint disjunctionExpressionConstraint : disjunctionExpressionConstraints) {
				BoolQueryBuilder shouldQuery = boolQuery();
				shouldQueries.should(shouldQuery);
				disjunctionExpressionConstraint.addCriteria(new SubRefinementBuilder(refinementBuilder, shouldQuery));
			}
		}
		if (exclusionExpressionConstraint != null) {
			BoolQueryBuilder mustNotQuery = boolQuery();
			refinementBuilder.getQuery().mustNot(mustNotQuery);
			exclusionExpressionConstraint.addCriteria(new SubRefinementBuilder(refinementBuilder, mustNotQuery));
		}
	}

	public void setConjunctionExpressionConstraints(List<SubExpressionConstraint> conjunctionExpressionConstraints) {
		this.conjunctionExpressionConstraints = conjunctionExpressionConstraints;
	}

	public void setDisjunctionExpressionConstraints(List<SubExpressionConstraint> disjunctionExpressionConstraints) {
		this.disjunctionExpressionConstraints = disjunctionExpressionConstraints;
	}

	public void setExclusionExpressionConstraint(SubExpressionConstraint exclusionExpressionConstraint) {
		this.exclusionExpressionConstraint = exclusionExpressionConstraint;
	}

	@Override
	public String toString() {
		return "CompoundExpressionConstraint{" +
				"conjunctionExpressionConstraints=" + conjunctionExpressionConstraints +
				", disjunctionExpressionConstraints=" + disjunctionExpressionConstraints +
				", exclusionExpressionConstraint=" + exclusionExpressionConstraint +
				'}';
	}
}
