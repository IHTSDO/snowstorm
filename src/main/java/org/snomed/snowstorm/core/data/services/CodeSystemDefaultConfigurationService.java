package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.services.pojo.CodeSystemDefaultConfiguration;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CodeSystemDefaultConfigurationService {

	private final Map<String, String> config = new HashMap<>();

	private Set<CodeSystemDefaultConfiguration> configurations;

	public Map<String, String> getConfig() {
		return config;
	}

	public String getDefaultModuleId(String codeSystemShortName) {
		for (CodeSystemDefaultConfiguration codeSystemConfiguration : configurations) {
			if (codeSystemConfiguration.getShortName().equalsIgnoreCase(codeSystemShortName)) {
				return codeSystemConfiguration.getModule();
			}
		}
		return null;
	}

	@PostConstruct
	private void init() {
		configurations = new HashSet<>();
		for (String key : config.keySet()) {
			String codeSystemShortName = key.substring(key.lastIndexOf(".") + 1);
			String configString = config.get(key);
			String[] split = configString.split("\\|");
			String name = split[0];
			String moduleId = split[1];
			String countryCode = split.length > 2 ? split[2] : null;
			String owner = split.length > 3 ? split[3] : null;
			configurations.add(new CodeSystemDefaultConfiguration(name, codeSystemShortName, moduleId, countryCode, owner));
		}
	}

	public Set<CodeSystemDefaultConfiguration> getConfigurations() {
		return configurations;
	}
}
