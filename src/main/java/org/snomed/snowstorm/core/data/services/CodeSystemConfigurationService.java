package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.services.pojo.CodeSystemConfiguration;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CodeSystemConfigurationService {

	private Map<String, String> config = new HashMap<>();

	private Set<CodeSystemConfiguration> configurations;

	public Map<String, String> getConfig() {
		return config;
	}

	public CodeSystemConfiguration findByModule(String moduleId) {
		for (CodeSystemConfiguration codeSystemConfiguration : configurations) {
			if (codeSystemConfiguration.getModule().equals(moduleId)) {
				return codeSystemConfiguration;
			}
		}
		return null;
	}
	
	public String getDefaultModuleId(String codeSystemShortName) {
		for (CodeSystemConfiguration codeSystemConfiguration : configurations) {
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
			String owner = split.length > 2 ? split[2] : null;
			configurations.add(new CodeSystemConfiguration(name, codeSystemShortName, moduleId, owner));
		}
	}

	public Set<CodeSystemConfiguration> getConfigurations() {
		return configurations;
	}
}
