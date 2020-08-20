package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SRefinement;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.MatchContext;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;

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
		Set<String> conceptIds = newHashSet();
		if (attribute != null) {
			conceptIds.addAll(((SEclAttribute) attribute).getConceptIds());
		}
		if (attributeSet != null) {
			conceptIds.addAll(((SEclAttributeSet) attributeSet).getConceptIds());
		}
		return conceptIds;
	}

	public void checkConceptConstraints(MatchContext matchContext) {
		if (attribute != null) {
			((SEclAttribute)attribute).checkConceptConstraints(matchContext);
		} else {
			((SEclAttributeSet)attributeSet).isMatch(matchContext);
		}
	}
}
