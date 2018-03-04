package org.snomed.snowstorm.ecl;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.snomed.snowstorm.ecl.domain.*;
import org.snomed.snowstorm.ecl.generated.ImpotentECLListener;
import org.snomed.snowstorm.ecl.generated.parser.ECLLexer;
import org.snomed.snowstorm.ecl.generated.parser.ECLParser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
class ECLQueryBuilder {

	ExpressionConstraint createQuery(String ecl) throws ECLException {
		ANTLRInputStream inputStream = new ANTLRInputStream(ecl);
		final ECLLexer lexer = new ECLLexer(inputStream);
		final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
		final ECLParser parser = new ECLParser(tokenStream);
		parser.setErrorHandler(new BailErrorStrategy());

		ParserRuleContext tree;
		try {
			tree = parser.expressionconstraint();
		} catch (NullPointerException e) {
			throw new ECLException("Failed to parse ECL '" + ecl + "'", e);
		}
		final ParseTreeWalker walker = new ParseTreeWalker();
		final ECLListenerImpl listener = new ECLListenerImpl();
		walker.walk(listener, tree);

		return listener.getRootExpressionConstraint();
	}

	private static final class ECLListenerImpl extends ImpotentECLListener {

		private ExpressionConstraint rootExpressionConstraint;

		@Override
		public void enterExpressionconstraint(ECLParser.ExpressionconstraintContext ctx) {
			ExpressionConstraint expressionConstraint = build(ctx);
			if (rootExpressionConstraint == null) {
				rootExpressionConstraint = expressionConstraint;
			}
		}

		private ExpressionConstraint build(ECLParser.ExpressionconstraintContext expressionconstraint) {
			if (expressionconstraint.refinedexpressionconstraint() != null) {
				return build(expressionconstraint.refinedexpressionconstraint());
			}
			if (expressionconstraint.compoundexpressionconstraint() != null) {
				return build(expressionconstraint.compoundexpressionconstraint());
			}
			if (expressionconstraint.dottedexpressionconstraint() != null) {
				return build(expressionconstraint.dottedexpressionconstraint());
			}
			if (expressionconstraint.subexpressionconstraint() != null) {
				return build(expressionconstraint.subexpressionconstraint());
			}
			return null;
		}

		public RefinedExpressionConstraint build(ECLParser.RefinedexpressionconstraintContext ctx) {
			return new RefinedExpressionConstraint(build(ctx.subexpressionconstraint()), build(ctx.eclrefinement()));
		}

		public CompoundExpressionConstraint build(ECLParser.CompoundexpressionconstraintContext ctx) {
			CompoundExpressionConstraint compoundExpressionConstraint = new CompoundExpressionConstraint();
			if (ctx.conjunctionexpressionconstraint() != null) {
				compoundExpressionConstraint.setConjunctionExpressionConstraints(build(ctx.conjunctionexpressionconstraint().subexpressionconstraint()));
			}
			if (ctx.disjunctionexpressionconstraint() != null) {
				compoundExpressionConstraint.setDisjunctionExpressionConstraints(build(ctx.disjunctionexpressionconstraint().subexpressionconstraint()));
			}
			if (ctx.exclusionexpressionconstraint() != null) {
				throw new UnsupportedOperationException("CompoundexpressionconstraintContext with exclusionexpressionconstraint is not supported.");
			}
			return compoundExpressionConstraint;
		}

		public DottedExpressionConstraint build(ECLParser.DottedexpressionconstraintContext ctx) {
			SubExpressionConstraint subExpressionConstraint = build(ctx.subexpressionconstraint());
			DottedExpressionConstraint dottedExpressionConstraint = new DottedExpressionConstraint(subExpressionConstraint);
			for (ECLParser.DottedexpressionattributeContext dotCtx : ctx.dottedexpressionattribute()) {
				SubExpressionConstraint attributeSubExpressionConstraint = build(dotCtx.eclattributename().subexpressionconstraint());
				dottedExpressionConstraint.addDottedAttribute(attributeSubExpressionConstraint);
			}
			return dottedExpressionConstraint;
		}

		private List<SubExpressionConstraint> build(List<ECLParser.SubexpressionconstraintContext> subExpressionConstraints) {
			return subExpressionConstraints.stream().map(this::build).collect(Collectors.toList());
		}

		public void enterExclusionexpressionconstraint(ECLParser.ExclusionexpressionconstraintContext ctx) {
			throw new UnsupportedOperationException("Exclusionexpressionconstraint is not supported.");
		}

		private SubExpressionConstraint build(ECLParser.SubexpressionconstraintContext ctx) {
			Operator operator = ctx.constraintoperator() != null ? Operator.textLookup(ctx.constraintoperator().getText()) : null;
			if (ctx.memberof() != null) {
				operator = Operator.memberOf;
			}

			SubExpressionConstraint subExpressionConstraint = new SubExpressionConstraint(operator);

			ECLParser.EclfocusconceptContext eclfocusconcept = ctx.eclfocusconcept();
			if (eclfocusconcept != null) {
				if (eclfocusconcept.wildcard() != null) {
					subExpressionConstraint.wildcard();
				}
				if (eclfocusconcept.eclconceptreference() != null) {
					subExpressionConstraint.setConceptId(eclfocusconcept.eclconceptreference().conceptid().getText());
				}
			} else {
				if (operator == Operator.memberOf) {
					throw new UnsupportedOperationException("MemberOf nested expression constraint is not supported.");
				}
				subExpressionConstraint.setNestedExpressionConstraint(build(ctx.expressionconstraint()));
			}

			return subExpressionConstraint;
		}

		private EclRefinement build(ECLParser.EclrefinementContext ctx) {
			if (ctx == null) {
				return null;
			}
			EclRefinement refinement = new EclRefinement();
			refinement.setSubRefinement(build(ctx.subrefinement()));
			if (ctx.conjunctionrefinementset() != null) {
				refinement.setConjunctionSubRefinements(buildSubRefinements(ctx.conjunctionrefinementset().subrefinement()));
			}
			if (ctx.disjunctionrefinementset() != null) {
				refinement.setDisjunctionSubRefinements(buildSubRefinements(ctx.disjunctionrefinementset().subrefinement()));
			}
			return refinement;
		}

		private List<SubRefinement> buildSubRefinements(List<ECLParser.SubrefinementContext> subrefinements) {
			return subrefinements.stream().map(this::build).collect(Collectors.toList());
		}

		private SubRefinement build(ECLParser.SubrefinementContext ctx) {
			SubRefinement subRefinement = new SubRefinement();
			subRefinement.setEclAttributeSet(build(ctx.eclattributeset()));
			subRefinement.setEclAttributeGroup(build(ctx.eclattributegroup()));
			subRefinement.setEclRefinement(build(ctx.eclrefinement()));
			return subRefinement;
		}

		private EclAttributeSet build(ECLParser.EclattributesetContext ctx) {
			if (ctx == null) return null;
			EclAttributeSet eclAttributeSet = new EclAttributeSet();
			eclAttributeSet.setSubAttributeSet(build(ctx.subattributeset()));
			if (ctx.conjunctionattributeset() != null) {
				eclAttributeSet.setConjunctionAttributeSet(buildSubAttributeSet(ctx.conjunctionattributeset().subattributeset()));
			}
			if (ctx.disjunctionattributeset() != null) {
				eclAttributeSet.setDisjunctionAttributeSet(buildSubAttributeSet(ctx.disjunctionattributeset().subattributeset()));
			}
			return eclAttributeSet;
		}

		private List<SubAttributeSet> buildSubAttributeSet(List<ECLParser.SubattributesetContext> subattributeset) {
			return subattributeset.stream().map(this::build).collect(Collectors.toList());
		}

		private SubAttributeSet build(ECLParser.SubattributesetContext ctx) {
			if (ctx == null) return null;
			SubAttributeSet subAttributeSet = new SubAttributeSet();
			subAttributeSet.setAttribute(build(ctx.eclattribute()));
			subAttributeSet.setAttributeSet(build(ctx.eclattributeset()));
			return subAttributeSet;
		}

		private EclAttributeGroup build(ECLParser.EclattributegroupContext ctx) {
			if (ctx == null) return null;

			if (ctx.cardinality() != null) {
				throw new UnsupportedOperationException("Group cardinality is not supported.");
			}

			return new EclAttributeGroup(build(ctx.eclattributeset()));
		}

		private EclAttribute build(ECLParser.EclattributeContext ctx) {
			if (ctx == null) return null;
			EclAttribute attribute = new EclAttribute();

			// TODO cardinality
			ECLParser.CardinalityContext cardinality = ctx.cardinality();
			if (cardinality != null) {
				throw new UnsupportedOperationException("Cardinality is not supported.");
			}

			if (ctx.reverseflag() != null) {
				attribute.reverse();
			}

			attribute.setAttributeName(build(ctx.eclattributename().subexpressionconstraint()));
			ECLParser.ExpressioncomparisonoperatorContext expressioncomparisonoperator = ctx.expressioncomparisonoperator();
			if (expressioncomparisonoperator == null) {
				throw new UnsupportedOperationException("Only the ExpressionComparisonOperator is supported. NumericComparisonOperator and StringComparisonOperator are not supported.");
			}
			attribute.setExpressionComparisonOperator(expressioncomparisonoperator.getText());
			attribute.setValue(build(ctx.subexpressionconstraint()));

			return attribute;
		}

		ExpressionConstraint getRootExpressionConstraint() {
			return rootExpressionConstraint;
		}

	}

}
