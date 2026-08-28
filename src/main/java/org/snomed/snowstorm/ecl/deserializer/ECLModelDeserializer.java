package org.snomed.snowstorm.ecl.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.Refinement;
import org.snomed.snowstorm.core.data.services.RuntimeServiceException;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SCompoundExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SDottedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SRefinedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.refinement.SEclRefinement;
import tools.jackson.core.JacksonException;

public class ECLModelDeserializer extends StdDeserializer<ExpressionConstraint> {

	public ECLModelDeserializer() {
		super(ExpressionConstraint.class);
	}

	/*
	Jackson 3 mappers are immutable, so a deserializer can no longer hold the mapper it is being
	registered on. readTreeAsValue resolves against the calling context, which carries the fully
	configured mapper - including this module - so nested constraints still deserialise.
	*/
	@Override
	public ExpressionConstraint deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
		JsonNode node = deserializationContext.readTree(jsonParser);
		if (node.get("dottedAttributes") != null) {
			return deserializationContext.readTreeAsValue(node, SDottedExpressionConstraint.class);
		}
		if (node.get("eclRefinement") != null) {
			return deserializationContext.readTreeAsValue(node, SRefinedExpressionConstraint.class);
		}
		if (node.get("conjunctionExpressionConstraints") != null ||
				node.get("disjunctionExpressionConstraints") != null ||
				node.get("exclusionExpressionConstraints") != null) {
			return deserializationContext.readTreeAsValue(node, SCompoundExpressionConstraint.class);
		}
		return deserializationContext.readTreeAsValue(node, SSubExpressionConstraint.class);
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
