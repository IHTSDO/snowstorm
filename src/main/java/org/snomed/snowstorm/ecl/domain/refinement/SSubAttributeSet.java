package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SRefinement;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.MatchContext;

import java.util.Set;

public class SSubAttributeSet extends SubAttributeSet implements SRefinement {

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		if (attribute != null) {
			((SEclAttribute)attribute).addCriteria(refinementBuilder);
		} else {
			((SEclAttributeSet)attributeSet).addCriteria(refinementBuilder);
		}
	}

	@Override
	public Set<String> getConceptIds() {
		if (attribute != null) {
			return ((SEclAttribute) attribute).getConceptIds();
		}
		return ((SEclAttributeSet) attributeSet).getConceptIds();
	}

	public void checkConceptConstraints(MatchContext matchContext) {
		if (attribute != null) {
			((SEclAttribute)attribute).checkConceptConstraints(matchContext);
		} else {
			((SEclAttributeSet)attributeSet).isMatch(matchContext);
		}
	}
}
