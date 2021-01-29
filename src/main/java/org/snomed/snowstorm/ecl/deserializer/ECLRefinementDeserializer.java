package org.snomed.snowstorm.ecl.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.langauges.ecl.domain.refinement.Refinement;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SCompoundExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SDottedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SRefinedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.refinement.SEclAttribute;
import org.snomed.snowstorm.ecl.domain.refinement.SEclRefinement;

import java.io.IOException;

public class ECLRefinementDeserializer extends StdDeserializer<EclRefinement> {

	private final ObjectMapper mapper;

	public ECLRefinementDeserializer(ObjectMapper mapper, Class a) {
		super(a);
		this.mapper = mapper;
	}

	@Override
	public EclRefinement deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
		JsonNode node = jsonParser.getCodec().readTree(jsonParser);
		return mapper.readValue(node.toString(), SEclRefinement.class);

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
