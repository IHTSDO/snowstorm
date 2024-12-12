package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.ReferenceSetTypeExportConfiguration;

import java.util.*;
import java.util.stream.Collectors;

public class ReferenceSetTypesConfigurationService {

	private final Map<String, String> types = new HashMap<>();

	public Map<String, String> getTypes() {
		return types;
	}

	public List<ReferenceSetTypeExportConfiguration> getConfiguredTypes() {
		Set<ReferenceSetTypeExportConfiguration> setTypes = new HashSet<>();
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
			setTypes.add(new ReferenceSetTypeExportConfiguration(conceptId, name, fieldNames, fieldTypes, exportDir));
		}
		return setTypes.stream().sorted(Comparator.comparing(ReferenceSetTypeExportConfiguration::getName)).collect(Collectors.toList());
	}

}
