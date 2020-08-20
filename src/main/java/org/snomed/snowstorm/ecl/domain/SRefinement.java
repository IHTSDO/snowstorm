package org.snomed.snowstorm.ecl.domain;

import java.util.Set;

public interface SRefinement {
	void addCriteria(RefinementBuilder refinementBuilder);

	Set<String> getConceptIds();
}
