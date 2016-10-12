package com.kaicube.snomed.elasticsnomed.repositories.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kaicube.snomed.elasticsnomed.domain.Description;
import com.kaicube.snomed.elasticsnomed.domain.Relationship;

import java.util.Set;

public abstract class ConceptStoreMixIn {

	@JsonIgnore
	abstract String getId();

	@JsonIgnore
	abstract int getGroupId();

	@JsonIgnore
	abstract String getDefinitionStatus();

	@JsonIgnore
	abstract Set<Description> getDescriptions();

	@JsonIgnore
	abstract Set<Relationship> getRelationships();

}
