package org.snomed.snowstorm.ecl.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface SRefinement {

	void addCriteria(RefinementBuilder refinementBuilder, Consumer<List<Long>> filteredOrSupplementedContentCallback, boolean triedCache);

	@JsonIgnore
	Set<String> getConceptIds();
}
