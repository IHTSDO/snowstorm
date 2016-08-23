package com.kaicube.snomed.elasticsnomed.domain;

import java.util.Set;

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
