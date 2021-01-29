package org.snomed.snowstorm.ecl.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class GenericJsonDeserializer<T> extends JsonDeserializer<T> {

	private final Class<T> type;

	public GenericJsonDeserializer(Class<T> type) {
		this.type = type;
	}

	@Override
	public T deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
		return (T) jsonParser.readValueAs(type);
	}
}
