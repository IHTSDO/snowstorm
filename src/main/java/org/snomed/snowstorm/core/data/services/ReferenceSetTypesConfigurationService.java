package org.snomed.snowstorm.core.data.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ReferenceSetType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ReferenceSetTypesConfigurationService {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceSetTypesConfigurationService.class);

	private Map<String, String> types = new HashMap<>();

	public Map<String, String> getTypes() {
		return types;
	}

	public Set<ReferenceSetType> getConfiguredTypes() {
		LOGGER.info("Supported reference set types for import/export: {}", types.keySet());
		Set<ReferenceSetType> setTypes = new HashSet<>();
		for (String key : types.keySet()) {
			String name = key.substring(key.lastIndexOf(".") + 1);
			String configString = types.get(key);
			String[] split = configString.split("\\|");
			String conceptId = split[0];
			String exportDir = split[1];
			String fieldTypes;
			String fieldNames;
			if (configString.endsWith("||")) {
				// Simple refset type
				fieldTypes = "";
				fieldNames = "";
			} else {
				fieldTypes = split[2];
				fieldNames = split[3];
			}
			setTypes.add(new ReferenceSetType(name, conceptId, fieldNames, fieldTypes, exportDir));
		}
		return setTypes;
	}

}
