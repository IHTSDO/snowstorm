package org.ihtsdo.elasticsnomed.rest.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.rest.View;

import java.util.Set;

public class ConceptDescriptionsResult {

	private Set<Description> conceptDescriptions;

	public ConceptDescriptionsResult(Set<Description> conceptDescriptions) {
		this.conceptDescriptions = conceptDescriptions;
	}

	@JsonView(value = View.Component.class)
	public Set<Description> getConceptDescriptions() {
		return conceptDescriptions;
	}
}
