package org.snomed.snowstorm.ecl.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;

import java.io.IOException;

public class SubExpressionDeserializer extends StdDeserializer<SubExpressionConstraint> {

	private final ECLModelDeserializer deserializer;

	public SubExpressionDeserializer(ECLModelDeserializer deserializer) {
		super((Class<?>) null);
		this.deserializer = deserializer;
	}

	@Override
	public SubExpressionConstraint deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
		return (SubExpressionConstraint) deserializer.deserialize(jsonParser, deserializationContext);
	}
}
