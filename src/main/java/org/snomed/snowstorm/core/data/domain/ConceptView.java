package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Set;

@JsonDeserialize(as = Concept.class)
public interface ConceptView {
	String getConceptId();

	String getFsn();

	String getEffectiveTime();

	boolean isActive();

	String getModuleId();

	String getDefinitionStatusId();

	Description getDescription(String descriptionId);

	Set<Description> getDescriptions();

	Set<Relationship> getRelationships();

	Set<Axiom> getAdditionalAxioms();

	Set<Axiom> getGciAxioms();
}
