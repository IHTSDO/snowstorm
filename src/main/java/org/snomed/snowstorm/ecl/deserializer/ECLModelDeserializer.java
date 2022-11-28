package org.snomed.snowstorm.ecl.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.Refinement;
import org.snomed.snowstorm.core.data.services.RuntimeServiceException;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SCompoundExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SDottedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SRefinedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.refinement.SEclRefinement;

import java.io.IOException;

public class ECLModelDeserializer extends StdDeserializer<ExpressionConstraint> {

	private final ObjectMapper mapper;

	public ECLModelDeserializer(ObjectMapper mapper, Class<?> vc) {
		super(vc);
		this.mapper = mapper;
	}

	@Override
	public ExpressionConstraint deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
		JsonNode node = jsonParser.getCodec().readTree(jsonParser);
		if (node.get("dottedAttributes") != null) {
			return mapper.readValue(node.toString(), SDottedExpressionConstraint.class);
		}
		if (node.get("eclRefinement") != null) {
			return mapper.readValue(node.toString(), SRefinedExpressionConstraint.class);
		}
		if (node.get("conjunctionExpressionConstraints") != null ||
				node.get("disjunctionExpressionConstraints") != null ||
				node.get("exclusionExpressionConstraints") != null) {
			return mapper.readValue(node.toString(), SCompoundExpressionConstraint.class);
		}
		return mapper.readValue(node.toString(), SSubExpressionConstraint.class);
	}

	public static void expressionConstraintToString(ExpressionConstraint expressionConstraint, StringBuffer buffer) {
		if (expressionConstraint instanceof SDottedExpressionConstraint) {
			((SDottedExpressionConstraint) expressionConstraint).toString(buffer);
		}
		if (expressionConstraint instanceof SRefinedExpressionConstraint) {
			((SRefinedExpressionConstraint) expressionConstraint).toString(buffer);
		}
		if (expressionConstraint instanceof SCompoundExpressionConstraint) {
			((SCompoundExpressionConstraint) expressionConstraint).toString(buffer);
		}
		if (expressionConstraint instanceof SSubExpressionConstraint) {
			((SSubExpressionConstraint) expressionConstraint).toString(buffer);
		}
	}

	public static void expressionConstraintToString(SEclRefinement refinement, StringBuffer buffer) {
		refinement.toString(buffer);
	}

	public static void expressionConstraintToString(Refinement refinement, StringBuffer buffer) {
		throw new RuntimeServiceException(String.format("Unknown refinement %s, %s", refinement.getClass(), refinement.toString()));
	}

}
