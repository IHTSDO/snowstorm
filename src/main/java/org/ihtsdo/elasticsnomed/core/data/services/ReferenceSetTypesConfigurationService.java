package org.ihtsdo.elasticsnomed.core.data.services;

import org.ihtsdo.elasticsnomed.core.data.domain.ReferenceSetType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ReferenceSetTypesConfigurationService {

	private Map<String, String> types = new HashMap<>();

	public Map<String, String> getTypes() {
		return types;
	}

	public Set<ReferenceSetType> getConfiguredTypes() {
		Set<ReferenceSetType> setTypes = new HashSet<>();
		for (String configString : types.values()) {
			String[] split = configString.split("\\|");
			String conceptId = split[0];
			String exportDir = split[1];
			String fieldTypes = split[2];
			String fieldNames = split[3];
			setTypes.add(new ReferenceSetType(conceptId, fieldNames, fieldTypes, exportDir));
		}
		return setTypes;
	}

}
