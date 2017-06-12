package org.ihtsdo.elasticsnomed.core.data.services.authoringmirror;

import org.ihtsdo.elasticsnomed.core.data.domain.Concept;

import java.util.Set;

public class ConceptChange {

	private Concept concept;
	private Set<ComponentChange> changes;

	public ConceptChange() {
	}

	public Concept getConcept() {
		return concept;
	}

	public void setConcept(Concept concept) {
		this.concept = concept;
	}

	public Set<ComponentChange> getChanges() {
		return changes;
	}

	public void setChanges(Set<ComponentChange> changes) {
		this.changes = changes;
	}
}
