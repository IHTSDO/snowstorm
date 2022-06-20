package org.snomed.snowstorm.fhir.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.pojo.FHIRSnomedConceptMapConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FHIRConceptMapImplicitConfig {

	private Map<String, String> snomedImplicit = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Map<String, String> getSnomedImplicit() {
		return snomedImplicit;
	}

	public List<FHIRSnomedConceptMapConfig> getImplicitMaps() {
		List<FHIRSnomedConceptMapConfig> maps = new ArrayList<>();
		for (Map.Entry<String, String> configEntry : snomedImplicit.entrySet()) {
			String refsetId = configEntry.getKey();
			String[] split = configEntry.getValue().split("\\|");
			if (split.length < 3 || split.length > 4) {
				logger.error("Value of configuration item 'fhir.conceptmap.snomed-implicit.{}' has an incorrect format. Expected 3 or four values separated by pipes, got '{}'.",
						refsetId, configEntry.getValue());
			}
			String name = split[0];
			String sourceSystem = split[1];
			String targetSystem = split[2];
			String equivalence = split.length == 4 ? split[3] : null;
			maps.add(new FHIRSnomedConceptMapConfig(refsetId, name, sourceSystem, targetSystem, equivalence));
		}
		logger.info("{} implicit FHIR ConceptMaps configured for SNOMED CT.", maps.size());
		return maps;
	}
}
