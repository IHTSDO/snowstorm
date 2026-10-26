package org.snomed.snowstorm.ecl.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;

public class SubExpressionDeserializer extends StdDeserializer<SubExpressionConstraint> {

	private final ECLModelDeserializer deserializer;

	public SubExpressionDeserializer(ECLModelDeserializer deserializer) {
		super(SubExpressionConstraint.class);
		this.deserializer = deserializer;
	}

	@Override
	public SubExpressionConstraint deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
		return (SubExpressionConstraint) deserializer.deserialize(jsonParser, deserializationContext);
	}
}
