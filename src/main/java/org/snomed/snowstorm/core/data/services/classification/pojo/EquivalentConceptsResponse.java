package org.snomed.snowstorm.core.data.services.classification.pojo;

import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.rest.pojo.ItemsPage;

import java.util.Set;

public class EquivalentConceptsResponse {

	private ItemsPage<ConceptMini> equivalentConcepts;

	public EquivalentConceptsResponse(Set<ConceptMini> equivalentConcepts) {
		this.equivalentConcepts = new ItemsPage<>(equivalentConcepts);
	}

	public ItemsPage<ConceptMini> getEquivalentConcepts() {
		return equivalentConcepts;
	}

}
