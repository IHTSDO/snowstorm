package org.snomed.snowstorm.core.data.repositories.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;
import java.util.Set;

public abstract class DescriptionStoreMixIn {

	@JsonIgnore
	abstract Map<String, String> getAcceptabilityMap();

	@JsonIgnore
	abstract String getType();

	@JsonIgnore
	abstract String getLang();

	@JsonIgnore
	abstract String getCaseSignificance();

	@JsonIgnore
	abstract String getInactivationIndicator();

	@JsonIgnore
	abstract Map<String, Set<String>> getAssociationTargets();

	@JsonIgnore
	abstract Map<String, String> getAcceptabilityMapFromLangRefsetMembers();

}
