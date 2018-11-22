package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
/*
 * Helper to allow Snowstorm branch metadata with array and object values.
 */
public class BranchMetadataHelper {

	@Autowired
	private ObjectMapper objectMapper;

	private static final String OBJECT_PREFIX = "{object}|";

	public Map<String, String> flattenObjectValues(Map<String, Object> metadataWithPossibleObjectValues) {
		if (metadataWithPossibleObjectValues == null) return null;

		Map<String, String> flatMap = new HashMap<>();
		for (String key : metadataWithPossibleObjectValues.keySet()) {
			Object object = metadataWithPossibleObjectValues.get(key);
			if (object instanceof String) {
				flatMap.put(key, (String) object);
			} else {
				try {
					flatMap.put(key, OBJECT_PREFIX + objectMapper.writeValueAsString(object));
				} catch (JsonProcessingException e) {
					throw new RuntimeServiceException("Failed to serialise branch metadata", e);
				}
			}
		}
		return flatMap;
	}

	public Map<String, Object> expandObjectValues(Map<String, String> metadata) {
		if (metadata == null) return null;

		Map<String, Object> fatMap = new HashMap<>();
		for (String key : metadata.keySet()) {
			String stringValue = metadata.get(key);
			if (stringValue != null && stringValue.startsWith(OBJECT_PREFIX)) {
				stringValue = stringValue.substring(OBJECT_PREFIX.length());
				try {
					if (stringValue.startsWith("[")) {
						fatMap.put(key, objectMapper.readValue(stringValue, List.class));
					} else {
						fatMap.put(key, objectMapper.readValue(stringValue, Map.class));
					}
				} catch (IOException e) {
					throw new RuntimeServiceException("Failed to deserialise branch metadata", e);
				}
			} else {
				fatMap.put(key, stringValue);
			}
		}
		return fatMap;
	}
}
