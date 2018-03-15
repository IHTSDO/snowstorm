package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.refinement.EclRefinement;

public class RefinedExpressionConstraint extends ExpressionConstraint {

	private SubExpressionConstraint subexpressionConstraint;
	private EclRefinement eclRefinement;

	public RefinedExpressionConstraint(SubExpressionConstraint subExpressionConstraint, EclRefinement eclRefinement) {
		this.subexpressionConstraint = subExpressionConstraint;
		this.eclRefinement = eclRefinement;
	}

	@Override
	protected boolean isWildcard() {
		return false;
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		subexpressionConstraint.addCriteria(refinementBuilder);
		eclRefinement.addCriteria(refinementBuilder);
	}

	@Override
	public String toString() {
		return "RefinedExpressionConstraint{" +
				"subexpressionConstraint=" + subexpressionConstraint +
				", eclRefinement=" + eclRefinement +
				'}';
	}
}
