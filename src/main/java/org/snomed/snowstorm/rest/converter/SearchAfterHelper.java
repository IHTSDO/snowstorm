package org.snomed.snowstorm.rest.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Base64;

public class SearchAfterHelper {

	private static ObjectMapper objectMapper = new ObjectMapper();

	private static final TypeReference<Object[]> OBJECT_ARRAY_TYPE_REF = new TypeReference<Object[]>() {};

	public static String toSearchAfterToken(final Object[] searchAfter) {
		if (searchAfter == null) {
			return null;
		}

		try {
			return new String(Base64.getEncoder().encode(objectMapper.writeValueAsString(searchAfter).getBytes()));
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to serialize 'searchAfter' array", e);
		}
	}

	public static Object[] fromSearchAfterToken(final String searchAfterToken) {
		if (StringUtils.isEmpty(searchAfterToken)) {
			return null;
		}

		try {
			return objectMapper.readValue(Base64.getDecoder().decode(searchAfterToken), OBJECT_ARRAY_TYPE_REF);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to deserialize 'searchAfter' token", e);
		}
	}

	public static Object[] convertToTokenAndBack(Object[] value) {
		return fromSearchAfterToken(toSearchAfterToken(value));
	}

}
