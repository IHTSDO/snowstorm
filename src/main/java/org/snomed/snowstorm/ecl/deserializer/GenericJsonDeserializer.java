package org.snomed.snowstorm.ecl.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class GenericJsonDeserializer<T> extends ValueDeserializer<T> {

	private final Class<T> type;

	public GenericJsonDeserializer(Class<T> type) {
		this.type = type;
	}

	@Override
	public T deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
		return deserializationContext.readValue(jsonParser, type);
	}
}
