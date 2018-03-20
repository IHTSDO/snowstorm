package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;

public class SSubAttributeSet extends SubAttributeSet implements SRefinement {

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		if (attribute != null) {
			((SEclAttribute)attribute).addCriteria(refinementBuilder);
		} else {
			((SEclAttributeSet)attributeSet).addCriteria(refinementBuilder);
		}
	}

}
