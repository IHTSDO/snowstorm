package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

public abstract class RelationshipStoreMixIn {

	@JsonIgnore
	abstract Map<String, String> getAcceptabilityMap();

	@JsonIgnore
	abstract ConceptMini type();

	@JsonIgnore
	abstract ConceptMini destination();

	@JsonIgnore
	abstract String getModifier();

	@JsonIgnore
	abstract String getCharacteristicType();

}
