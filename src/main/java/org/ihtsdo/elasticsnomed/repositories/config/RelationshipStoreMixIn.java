package org.ihtsdo.elasticsnomed.repositories.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.ihtsdo.elasticsnomed.domain.ConceptMini;

import java.util.Map;

public abstract class RelationshipStoreMixIn {

	@JsonIgnore
	abstract Map<String, String> getAcceptabilityMap();

	@JsonIgnore
	abstract ConceptMini type();

	@JsonIgnore
	abstract ConceptMini target();

	@JsonIgnore
	abstract String getModifier();

	@JsonIgnore
	abstract String getCharacteristicType();

}
