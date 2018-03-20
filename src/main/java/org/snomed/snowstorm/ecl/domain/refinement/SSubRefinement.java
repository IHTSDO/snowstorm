package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;

public class SSubRefinement extends SubRefinement implements SRefinement {

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		if (eclAttributeSet != null) {
			((SEclAttributeSet)eclAttributeSet).addCriteria(refinementBuilder);
		} else if (eclAttributeGroup != null) {
			((SEclAttributeGroup)eclAttributeGroup).addCriteria(refinementBuilder);
		} else {
			((SEclRefinement)eclRefinement).addCriteria(refinementBuilder);
		}
	}

}
