package org.ihtsdo.elasticsnomed.core.data.repositories.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

public abstract class DescriptionStoreMixIn {

	@JsonIgnore
	abstract Map<String, String> getAcceptabilityMap();

	@JsonIgnore
	abstract String getType();

	@JsonIgnore
	abstract String getLang();

}
