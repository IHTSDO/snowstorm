package org.ihtsdo.elasticsnomed.core.data.services.authoringmirror;

import org.ihtsdo.elasticsnomed.core.data.domain.Concept;

public class ConceptChange {

	private Concept concept;

	public ConceptChange() {
	}

	public Concept getConcept() {
		return concept;
	}

	public void setConcept(Concept concept) {
		this.concept = concept;
	}
}
