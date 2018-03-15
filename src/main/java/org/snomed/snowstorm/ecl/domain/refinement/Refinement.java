package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.snowstorm.ecl.domain.RefinementBuilder;

public interface Refinement {

	void addCriteria(RefinementBuilder refinementBuilder);

}
