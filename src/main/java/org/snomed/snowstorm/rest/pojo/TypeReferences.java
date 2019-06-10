package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.snomed.snowstorm.core.data.domain.ConceptMini;

import java.util.Collection;

@JsonPropertyOrder({"referenceType", "referencingConcepts"})
public class TypeReferences {

	private ConceptMini referenceType;
	private Collection<ConceptMini> referencingConcepts;

	public TypeReferences(ConceptMini referenceType, Collection<ConceptMini> referencingConcepts) {
		this.referenceType = referenceType;
		this.referencingConcepts = referencingConcepts;
	}

	public ConceptMini getReferenceType() {
		return referenceType;
	}

	public Collection<ConceptMini> getReferencingConcepts() {
		return referencingConcepts;
	}
}
