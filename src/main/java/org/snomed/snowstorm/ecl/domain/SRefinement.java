package org.snomed.snowstorm.ecl.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Set;

public interface SRefinement {
	void addCriteria(RefinementBuilder refinementBuilder);

	@JsonIgnore
	Set<String> getConceptIds();
}
