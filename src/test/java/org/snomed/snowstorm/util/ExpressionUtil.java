package org.snomed.snowstorm.util;

import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.*;
import org.snomed.snowstorm.ecl.SECLObjectFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ExpressionUtil {

	public static ECLQueryBuilder eclQueryBuilder = new ECLQueryBuilder(new SECLObjectFactory());

	static final Comparator<SubExpressionConstraint> EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID = Comparator
			.comparing(SubExpressionConstraint::getConceptId, Comparator.nullsFirst(String::compareTo));

	public static String sortExpressionConstraintByConceptId(String expressionConstraint) {
		if (expressionConstraint == null || expressionConstraint.trim().isEmpty()) {
			return expressionConstraint;
		}
		ExpressionConstraint constraint = null;
		try {
			constraint = eclQueryBuilder.createQuery(expressionConstraint);
		} catch(ECLException e) {
			throw new IllegalStateException(String.format("Invalid ECL found %s", expressionConstraint), e);
		}

		if (constraint instanceof CompoundExpressionConstraint) {
			return constructExpression((CompoundExpressionConstraint) constraint);
		} else if (constraint instanceof RefinedExpressionConstraint) {
			RefinedExpressionConstraint refinedExpressionConstraint = (RefinedExpressionConstraint) constraint;
			return constructExpression(refinedExpressionConstraint);
		} else {
			return expressionConstraint;
		}
	}

	private static String constructExpression(CompoundExpressionConstraint compound) {
		StringBuilder expressionBuilder = new StringBuilder();
		if (compound.getConjunctionExpressionConstraints() != null) {
			List<SubExpressionConstraint> conJunctions = compound.getConjunctionExpressionConstraints();
			Collections.sort(conJunctions, EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID);
			for (SubExpressionConstraint subExpressionConstraint : conJunctions) {
				if (subExpressionConstraint.getConceptId() == null && subExpressionConstraint.getNestedExpressionConstraint() != null) {
					if (subExpressionConstraint.getNestedExpressionConstraint() instanceof  RefinedExpressionConstraint) {
						RefinedExpressionConstraint refinedExpressionConstraint = (RefinedExpressionConstraint) subExpressionConstraint.getNestedExpressionConstraint();
						refinedExpressionConstraint.getSubexpressionConstraint();
						refinedExpressionConstraint.getEclRefinement();
					}
				}
			}
			for (int i = 0; i < conJunctions.size(); i++) {
				if (i > 0) {
					expressionBuilder.append( " AND ");
				}
				expressionBuilder.append(constructExpression(conJunctions.get(i)));
			}
		}
		if (compound.getDisjunctionExpressionConstraints() != null) {
			List<SubExpressionConstraint> disJunctions = compound.getDisjunctionExpressionConstraints();
			List<RefinedExpressionConstraint> refinedExpressionConstraints = getNestedExpressionConstraint(disJunctions);
			if (!refinedExpressionConstraints.isEmpty()) {
				expressionBuilder.append(constructExpression(refinedExpressionConstraints, false));
			} else {
				Collections.sort(disJunctions, EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID);
				for (int i = 0; i < disJunctions.size(); i++) {
					if (i > 0) {
						expressionBuilder.append( " OR ");
					}
					expressionBuilder.append(constructExpression(disJunctions.get(i)));
				}
			}
		}

		if (compound.getExclusionExpressionConstraints() != null) {
			expressionBuilder.append(constructExpression(compound.getExclusionExpressionConstraints().getFirst()));
			expressionBuilder.append(" MINUS ");
			expressionBuilder.append(constructExpression(compound.getExclusionExpressionConstraints().getSecond()));
		}
		return expressionBuilder.toString();
	}

	private static String constructExpression(List<RefinedExpressionConstraint> refinedExpressionConstraints, boolean isConjunction) {
		StringBuilder expressionBuilder = new StringBuilder();
		Map<String, RefinedExpressionConstraint> conceptIdToConstraintMap = new HashMap<>();
		for (RefinedExpressionConstraint refined : refinedExpressionConstraints) {
			String conceptId = refined.getSubexpressionConstraint().getConceptId();
			if (conceptId != null) {
				conceptIdToConstraintMap.put(conceptId, refined);
			}
		}
		List<String> conceptIds = conceptIdToConstraintMap.keySet().stream().collect(Collectors.toList());
		Collections.sort(conceptIds);
		List<String> expressions = new ArrayList<>();
		for (String conceptId : conceptIds) {
			expressions.add(constructExpression(conceptIdToConstraintMap.get(conceptId)));
		}
		for (int i = 0; i < expressions.size(); i++) {
			if (i > 0) {
				if (isConjunction) {
					expressionBuilder.append(" AND ");
				} else {
					expressionBuilder.append(" OR ");
				}
			}
			if (expressions.size() > 0) {
				expressionBuilder.append("(");
			}
			expressionBuilder.append(expressions.get(i));
			if (expressions.size() > 0) {
				expressionBuilder.append(")");
			}
		}
		return expressionBuilder.toString();
	}

	private static List<RefinedExpressionConstraint> getNestedExpressionConstraint(List<SubExpressionConstraint> subExpressionConstraints) {
		List<RefinedExpressionConstraint> results = new ArrayList<>();
		for (SubExpressionConstraint subExpressionConstraint : subExpressionConstraints) {
			if (subExpressionConstraint.getConceptId() == null && subExpressionConstraint.getNestedExpressionConstraint() != null) {
				if (subExpressionConstraint.getNestedExpressionConstraint() instanceof  RefinedExpressionConstraint) {
					results.add((RefinedExpressionConstraint) subExpressionConstraint.getNestedExpressionConstraint());
				}
			}
		}
		return results;
	}

	public static String constructExpression(SubExpressionConstraint constraint) {
		StringBuilder expressionBuilder = new StringBuilder();
		if (constraint.getNestedExpressionConstraint() != null) {
			return "(" + constructExpression(constraint.getNestedExpressionConstraint()) + ")";
		} else {
			if (constraint.getOperator() != null) {
				expressionBuilder.append(constraint.getOperator().getText());
				expressionBuilder.append(" ");
			}
			expressionBuilder.append(constraint.getConceptId());
			expressionBuilder.append(" ");
			expressionBuilder.append("|");
			expressionBuilder.append(constraint.getTerm());
			expressionBuilder.append("|");
		}
		return expressionBuilder.toString();
	}

	private static String constructExpression(ExpressionConstraint expressionConstraint) {
		if (expressionConstraint instanceof SubExpressionConstraint) {
			return constructExpression((SubExpressionConstraint) expressionConstraint);
		} else if (expressionConstraint instanceof RefinedExpressionConstraint) {
			return constructExpression((RefinedExpressionConstraint) expressionConstraint);
		} else if (expressionConstraint instanceof CompoundExpressionConstraint) {
			return constructExpression((CompoundExpressionConstraint) expressionConstraint);
		} else {
			return null;
		}
	}


	public static String constructExpression(RefinedExpressionConstraint refinedExpressionConstraint) {
		StringBuilder expressionBuilder = new StringBuilder();
		if (refinedExpressionConstraint.getSubexpressionConstraint() != null) {
			expressionBuilder.append(constructExpression(refinedExpressionConstraint.getSubexpressionConstraint()));
			expressionBuilder.append(": ");
		}

		EclRefinement eclRefinement = refinedExpressionConstraint.getEclRefinement();
		if (eclRefinement != null) {
			SubRefinement subRefinement = eclRefinement.getSubRefinement();
			if (subRefinement != null) {
				expressionBuilder.append(constructExpression(subRefinement));
			}
			List<SubRefinement> conjunctionSubRefinements = eclRefinement.getConjunctionSubRefinements();
			if (conjunctionSubRefinements != null && !conjunctionSubRefinements.isEmpty()) {
				if (!expressionBuilder.toString().isEmpty()) {
					expressionBuilder.append(", ");
				}
				int counter = 0;
				for (SubRefinement conjunction : conjunctionSubRefinements) {
					if (counter++ > 0) {
						expressionBuilder.append(", ");
					}
					expressionBuilder.append(constructExpression(conjunction));
				}
			}
			List<SubRefinement> disjunctionSubRefinements = eclRefinement.getDisjunctionSubRefinements();
			if (disjunctionSubRefinements != null && !disjunctionSubRefinements.isEmpty()) {
				if (!expressionBuilder.toString().isEmpty()) {
					expressionBuilder.append(", ");
				}
				int counter = 0;
				for (SubRefinement disjunction : disjunctionSubRefinements) {
					if (counter++ > 0) {
						expressionBuilder.append(", ");
					}
					expressionBuilder.append(constructExpression(disjunction));
				}
			}
		}
		return expressionBuilder.toString();
	}

	private static String constructExpression(SubRefinement subRefinement) {
		StringBuilder builder = new StringBuilder();
		EclAttributeSet attributeSet = subRefinement.getEclAttributeSet();
		if (attributeSet != null) {
			if (attributeSet.getSubAttributeSet() != null) {
				builder.append(constructExpression(attributeSet.getSubAttributeSet().getAttribute()));
			}
			List<SubAttributeSet> disjunctionAttributeSet = attributeSet.getDisjunctionAttributeSet();
			if (disjunctionAttributeSet != null && !disjunctionAttributeSet.isEmpty()) {
				if (!builder.toString().isEmpty()) {
					builder.append(", ");
				}
				for (SubAttributeSet subAttributeSet : disjunctionAttributeSet) {
					EclAttribute eclAttribute = subAttributeSet.getAttribute();
					builder.append(constructExpression(eclAttribute));
				}
			}
			List<SubAttributeSet> conjunctionAttributeSet = attributeSet.getConjunctionAttributeSet();
			if (conjunctionAttributeSet != null && !conjunctionAttributeSet.isEmpty()) {
				if (!builder.toString().isEmpty()) {
					builder.append(", ");
				}
				int counter = 0;
				for (SubAttributeSet subAttributeSet : conjunctionAttributeSet) {
					if (counter ++ > 0) {
						builder.append(", ");
					}
					EclAttribute eclAttribute = subAttributeSet.getAttribute();
					if (eclAttribute != null) {
						builder.append(constructExpression(eclAttribute));
					} else if (subAttributeSet.getAttributeSet() != null) {
						builder.append(constructExpression(subAttributeSet.getAttributeSet()));
					}
				}
			}
		}
		EclRefinement eclRefinement = subRefinement.getEclRefinement();
		if (eclRefinement!= null) {
			if (eclRefinement.getSubRefinement() != null) {
				builder.append(constructExpression(eclRefinement.getSubRefinement()));
			}

			List<SubRefinement> disjunctionSubRefinements = eclRefinement.getDisjunctionSubRefinements();
			if (disjunctionSubRefinements != null) {
				for (SubRefinement disjunction : disjunctionSubRefinements) {
					builder.append(constructExpression(disjunction));
				}
			}
			List<SubRefinement> conjunctionSubRefinements = eclRefinement.getConjunctionSubRefinements();
			if (conjunctionSubRefinements != null) {
				for (SubRefinement conjunction : conjunctionSubRefinements) {
					builder.append(constructExpression(conjunction));
				}
			}
		}

		EclAttributeGroup attributeGroup = subRefinement.getEclAttributeGroup();
		if (attributeGroup != null) {
			if (!builder.toString().isEmpty()) {
				builder.append(", ");
			}
			builder.append("[");
			builder.append(attributeGroup.getCardinalityMin().intValue());
			builder.append("..");
			builder.append(attributeGroup.getCardinalityMax() == null ? "*" : attributeGroup.getCardinalityMax().intValue());
			builder.append("]");
			builder.append(" { ");
			builder.append(constructExpression(attributeGroup.getAttributeSet()));
			builder.append(" }");
		}
		return builder.toString();
	}

	private static String constructExpression(EclAttributeSet attributeSet) {
		StringBuilder builder = new StringBuilder();
		SubAttributeSet subAttributeSet = attributeSet.getSubAttributeSet();
		if (subAttributeSet != null) {
			builder.append(constructExpression(subAttributeSet.getAttribute()));
		}
		List<SubAttributeSet> disjunctionAttributeSet = attributeSet.getDisjunctionAttributeSet();
		if (disjunctionAttributeSet != null && !disjunctionAttributeSet.isEmpty()) {
			int counter = 0;
			for (SubAttributeSet disjunction : disjunctionAttributeSet) {
				if (counter++ > 0) {
					builder.append(" OR ");
				}
				builder.append(disjunction);
			}
		}
		List<SubAttributeSet> conjunctionAttributeSet = attributeSet.getConjunctionAttributeSet();
		if (conjunctionAttributeSet != null && !conjunctionAttributeSet.isEmpty()) {
			int counter = 0;
			for (SubAttributeSet conjunction : conjunctionAttributeSet) {
				if (counter++ > 0) {
					builder.append(" AND ");
				}
				builder.append(conjunction);
			}
		}
		return builder.toString();
	}

	private static String constructExpression(EclAttribute eclAttribute) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		builder.append(eclAttribute.getCardinalityMin().intValue());
		builder.append("..");
		builder.append(eclAttribute.getCardinalityMax() == null ? "*" : eclAttribute.getCardinalityMax().intValue());
		builder.append("] ");
		builder.append(eclAttribute.getAttributeName().getConceptId());
		builder.append(" |");
		builder.append(eclAttribute.getAttributeName().getTerm());
		builder.append("|");
		builder.append(" = ");
		if (eclAttribute.getValue() != null) {
			ExpressionConstraint nestedExpressionConstraint = eclAttribute.getValue().getNestedExpressionConstraint();
			if (nestedExpressionConstraint != null) {
				if (nestedExpressionConstraint instanceof RefinedExpressionConstraint) {
					builder.append(constructExpression((RefinedExpressionConstraint) nestedExpressionConstraint));
				} else if (nestedExpressionConstraint instanceof SubExpressionConstraint) {
					builder.append(constructExpression((SubExpressionConstraint) nestedExpressionConstraint));
				}
				if (nestedExpressionConstraint instanceof CompoundExpressionConstraint) {
					CompoundExpressionConstraint compoundAndNested = (CompoundExpressionConstraint) nestedExpressionConstraint;
					builder.append("(");
					builder.append(constructExpression(compoundAndNested));
					builder.append(")");

				}
			} else {
				if (eclAttribute.getValue().getOperator() != null) {
					builder.append(eclAttribute.getValue().getOperator().getText());
					builder.append(" ");
				}
				if (eclAttribute.getValue() != null) {
					if (eclAttribute.getValue().isWildcard()) {
						builder.append("*");
					} else {
						builder.append(eclAttribute.getValue().getConceptId());
						builder.append(" |");
						builder.append(eclAttribute.getValue().getTerm());
						builder.append("|");
					}
				}
			}
		}
		return builder.toString();
	}
}
