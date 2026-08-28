package org.snomed.snowstorm.rest.converter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import java.util.Base64;

public class SearchAfterHelper {

	// Produces the base64 pagination tokens handed to clients, so it uses the same Jackson 2
	// defaults as every other mapper rather than inheriting Jackson 3's.
	private static final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();

	private static final TypeReference<Object[]> OBJECT_ARRAY_TYPE_REF = new TypeReference<>() {};

	public static String toSearchAfterToken(final Object[] searchAfter) {
		if (searchAfter == null) {
			return null;
		}

		try {
			return new String(Base64.getEncoder().encode(objectMapper.writeValueAsString(searchAfter).getBytes()));
		} catch (JacksonException e) {
			throw new IllegalArgumentException("Failed to serialize 'searchAfter' array", e);
		}
	}

	public static Object[] fromSearchAfterToken(final String searchAfterToken) {
		if (StringUtils.isEmpty(searchAfterToken)) {
			return null;
		}

		try {
			return objectMapper.readValue(Base64.getDecoder().decode(searchAfterToken), OBJECT_ARRAY_TYPE_REF);
		} catch (JacksonException e) {
			throw new IllegalArgumentException(String.format("Failed to deserialize 'searchAfter' token: '%s'", searchAfterToken), e);
		}
	}

	public static Object[] convertToTokenAndBack(Object[] value) {
		return fromSearchAfterToken(toSearchAfterToken(value));
	}

	public static HttpHeaders getSearchAfterHeader(Object[] searchAfter) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("searchAfter", toSearchAfterToken(searchAfter));
		return headers;
	}
}
