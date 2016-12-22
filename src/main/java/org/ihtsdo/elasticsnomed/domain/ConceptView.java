package org.ihtsdo.elasticsnomed.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Set;

@JsonDeserialize(as = Concept.class)
public interface ConceptView {
	String getFsn();

	Description getDescription(String descriptionId);

	String getConceptId();

	String getEffectiveTime();

	boolean isActive();

	String getModuleId();

	String getDefinitionStatusId();

	Set<Description> getDescriptions();

	Set<Relationship> getRelationships();
}
