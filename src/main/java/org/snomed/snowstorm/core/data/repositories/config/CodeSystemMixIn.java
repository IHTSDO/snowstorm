package org.snomed.snowstorm.core.data.repositories.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.ConceptMini;

import java.util.Collection;
import java.util.Map;

public abstract class CodeSystemMixIn {

	@JsonIgnore
	abstract Map<String, String> getLanguages();

	@JsonIgnore
	abstract  Collection<ConceptMini> getModules();

	@JsonIgnore
	abstract  CodeSystemVersion getLatestVersion();


}
