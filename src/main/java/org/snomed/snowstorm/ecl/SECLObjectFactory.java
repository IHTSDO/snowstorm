package org.snomed.snowstorm.ecl;

import org.snomed.langauges.ecl.ECLObjectFactory;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.DottedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.*;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SCompoundExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SDottedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SRefinedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.refinement.*;

public class SECLObjectFactory extends ECLObjectFactory {

	@Override
	protected RefinedExpressionConstraint getRefinedExpressionConstraint(SubExpressionConstraint subExpressionConstraint, EclRefinement eclRefinement) {
		return new SRefinedExpressionConstraint(subExpressionConstraint, eclRefinement);
	}

	@Override
	protected CompoundExpressionConstraint getCompoundExpressionConstraint() {
		return new SCompoundExpressionConstraint();
	}

	@Override
	protected DottedExpressionConstraint getDottedExpressionConstraint(SubExpressionConstraint subExpressionConstraint) {
		return new SDottedExpressionConstraint(subExpressionConstraint);
	}

	@Override
	protected SubExpressionConstraint getSubExpressionConstraint(Operator operator) {
		return new SSubExpressionConstraint(operator);
	}

	@Override
	protected EclAttribute getAttribute() {
		return new SEclAttribute();
	}

	@Override
	protected EclAttributeGroup getAttributeGroup() {
		return new SEclAttributeGroup();
	}

	@Override
	protected EclAttributeSet getEclAttributeSet() {
		return new SEclAttributeSet();
	}

	@Override
	protected EclRefinement getRefinement() {
		return new SEclRefinement();
	}

	@Override
	protected SubAttributeSet getSubAttributeSet() {
		return new SSubAttributeSet();
	}

	@Override
	protected SubRefinement getSubRefinement() {
		return new SSubRefinement();
	}
}
