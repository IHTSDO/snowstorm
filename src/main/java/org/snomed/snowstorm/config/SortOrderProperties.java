package org.snomed.snowstorm.config;

import java.util.HashMap;
import java.util.Map;

public class SortOrderProperties {

	private Map<String, String> attribute = new HashMap<>();
	private Map<String, Map<Long, Short>> domainAttributeOrderMap;

	public Map<String, String> getAttribute() {
		return attribute;
	}

	public Map<String, Map<Long, Short>> getDomainAttributeOrderMap() {
		if (domainAttributeOrderMap == null) {
			synchronized (this) {
				domainAttributeOrderMap = new HashMap<>();
				try {
					for (String key : attribute.keySet()) {
						String[] parts = key.split("\\.");
						String semanticTag = parts[0];
						Long attributeId = Long.parseLong(parts[1]);
						short order = Short.parseShort(attribute.get(key));
						domainAttributeOrderMap.computeIfAbsent(semanticTag, id -> new HashMap<>())
								.put(attributeId, order);
					}
				} catch (NullPointerException | NumberFormatException e) {
					throw new IllegalArgumentException("Failed to process attribute sort order configuration. Please check format.", e);
				}
			}
		}
		return domainAttributeOrderMap;
	}
}
