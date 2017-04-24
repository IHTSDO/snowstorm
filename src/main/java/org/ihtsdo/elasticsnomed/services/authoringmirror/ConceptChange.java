package org.ihtsdo.elasticsnomed.services.authoringmirror;

import org.ihtsdo.elasticsnomed.domain.Concept;

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
