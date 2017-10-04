package org.ihtsdo.elasticsnomed.core.data.services.classification.pojo;

import java.util.Set;

public class EquivalentConceptsResponse {

	private Set<ConceptIdAndLabel> equivalentConcepts;

	public EquivalentConceptsResponse(Set<ConceptIdAndLabel> equivalentConcepts) {
		this.equivalentConcepts = equivalentConcepts;
	}

	public Set<ConceptIdAndLabel> getEquivalentConcepts() {
		return equivalentConcepts;
	}

	public static final class ConceptIdAndLabel {
		private String id;
		private String label;

		public ConceptIdAndLabel(String id, String label) {
			this.id = id;
			this.label = label;
		}

		public String getId() {
			return id;
		}

		public String getLabel() {
			return label;
		}
	}
}
