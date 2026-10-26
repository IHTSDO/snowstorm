package org.snomed.snowstorm.ecl.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.snowstorm.ecl.domain.refinement.SEclRefinement;

public class ECLRefinementDeserializer extends StdDeserializer<EclRefinement> {

	public ECLRefinementDeserializer() {
		super(EclRefinement.class);
	}

	@Override
	public EclRefinement deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
		JsonNode node = deserializationContext.readTree(jsonParser);
		return deserializationContext.readTreeAsValue(node, SEclRefinement.class);

//		if (node.get("subRefinement") != null) {
//			return mapper.readValue(node.toString(), SEclRefinement.class);
//		}
//		if (node.get("eclAttributeName") != null) {
//			return mapper.readValue(node.toString(), SEclAttribute.class);
//		}
//		if (node.get("conjunctionExpressionConstraints") != null ||
//				node.get("disjunctionExpressionConstraints") != null ||
//				node.get("exclusionExpressionConstraint") != null) {
//			return mapper.readValue(node.toString(), SCompoundExpressionConstraint.class);
//		}
//		return mapper.readValue(node.toString(), SSubExpressionConstraint.class);
	}
}
