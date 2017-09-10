package org.ihtsdo.elasticsnomed.ecl;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.ihtsdo.elasticsnomed.ecl.domain.*;
import org.ihtsdo.elasticsnomed.ecl.generated.ImpotentECLListener;
import org.ihtsdo.elasticsnomed.ecl.generated.parser.ECLLexer;
import org.ihtsdo.elasticsnomed.ecl.generated.parser.ECLParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ECLQueryBuilder {

	ExpressionConstraint createQuery(String ecl) throws ECLException {
		ANTLRInputStream inputStream = new ANTLRInputStream(ecl);
		final ECLLexer lexer = new ECLLexer(inputStream);
		final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
		final ECLParser parser = new ECLParser(tokenStream);
//		final List<RecognitionException> exceptions = new ArrayList<>();
//		parser.setErrorHandler(getErrorHandler(exceptions));

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

		private List<SubExpressionConstraint> build(List<ECLParser.SubexpressionconstraintContext> subExpressionConstraints) {
			return subExpressionConstraints.stream().map(this::build).collect(Collectors.toList());
		}

		public void enterExclusionexpressionconstraint(ECLParser.ExclusionexpressionconstraintContext ctx) {
			throw new UnsupportedOperationException("Exclusionexpressionconstraint is not supported.");
		}

		public void enterDottedexpressionconstraint(ECLParser.DottedexpressionconstraintContext ctx) {
			throw new UnsupportedOperationException("Dottedexpressionconstraint is not supported.");
		}

		private SubExpressionConstraint build(ECLParser.SubexpressionconstraintContext ctx) {
			Operator operator = ctx.constraintoperator() != null ? Operator.textLookup(ctx.constraintoperator().getText()) : null;

			if (ctx.memberof() != null) {
				throw new UnsupportedOperationException("MemberOf is not supported.");
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
			refinement.setConjunctionSubRefinements(build(ctx.conjunctionrefinementset()));
			if (ctx.disjunctionrefinementset() != null) {
				throw new UnsupportedOperationException("DisjunctionRefinementSet is not supported.");
			}
			return refinement;
		}

		public List<SubRefinement> build(ECLParser.ConjunctionrefinementsetContext ctx) {
			List<SubRefinement> refinements = new ArrayList<>();
			if (ctx != null) {
				for (ECLParser.SubrefinementContext subrefinementContext : ctx.subrefinement()) {
					refinements.add(build(subrefinementContext));
				}
			}
			return refinements;
		}

		private SubRefinement build(ECLParser.SubrefinementContext ctx) {
			SubRefinement subRefinement = new SubRefinement();
			subRefinement.setEclAttributeSet(build(ctx.eclattributeset()));

//			subRefinement.setEclAttributeGroup(build(ctx.eclattributegroup()));
			if (ctx.eclattributegroup() != null) {
				throw new UnsupportedOperationException("EclAttributeGroup is not supported.");
			}

//			subRefinement.setEclRefinement(build(ctx.eclrefinement()));
			if (ctx.eclrefinement() != null) {
				throw new UnsupportedOperationException("EclRefinement is not supported.");
			}

			return subRefinement;
		}

		private EclAttributeSet build(ECLParser.EclattributesetContext ctx) {
			if (ctx == null) return null;
			EclAttributeSet eclAttributeSet = new EclAttributeSet();
			eclAttributeSet.setSubAttributeSet(build(ctx.subattributeset()));
			ECLParser.ConjunctionattributesetContext conjunctionattributeset = ctx.conjunctionattributeset();
			if (conjunctionattributeset != null) {
				throw new UnsupportedOperationException("ConjunctionAttributeSet is not supported.");
//				for (ECLParser.SubattributesetContext subattributesetContext : conjunctionattributeset.subattributeset()) {
//					eclAttributeSet.addConjunctionAttributeSet(build(subattributesetContext));
//				}
			}
			ECLParser.DisjunctionattributesetContext disjunctionattributeset = ctx.disjunctionattributeset();
			if (disjunctionattributeset != null) {
				throw new UnsupportedOperationException("DisjunctionAttributeSet is not supported.");
//				for (ECLParser.SubattributesetContext subattributesetContext : disjunctionattributeset.subattributeset()) {
//					eclAttributeSet.addDisjunctionAttributeSet(build(subattributesetContext));
//				}
			}
			return eclAttributeSet;
		}

		private SubAttributeSet build(ECLParser.SubattributesetContext ctx) {
			if (ctx == null) return null;
			SubAttributeSet subAttributeSet = new SubAttributeSet();
			subAttributeSet.setAttribute(build(ctx.eclattribute()));

//			subAttributeSet.setAttributeSet(build(ctx.eclattributeset()));
			if (ctx.eclattributeset() != null) {
				throw new UnsupportedOperationException("EclAttributeSet is not supported.");
			}

			return subAttributeSet;
		}

		private EclAttributeSet build(ECLParser.EclattributegroupContext ctx) {
			return null;
		}

		private EclAttribute build(ECLParser.EclattributeContext ctx) {
			if (ctx == null) return null;
			EclAttribute attribute = new EclAttribute();

			// TODO cardinality
			ECLParser.CardinalityContext cardinality = ctx.cardinality();
			if (cardinality != null) {
				throw new UnsupportedOperationException("Cardinality is not supported.");
			}

			// TODO reverseflag
			ECLParser.ReverseflagContext reverseflag = ctx.reverseflag();
			if (reverseflag != null) {
				throw new UnsupportedOperationException("The reverse flag is not supported.");
			}

			attribute.setAttributeName(build(ctx.eclattributename().subexpressionconstraint()));
			ECLParser.ExpressioncomparisonoperatorContext expressioncomparisonoperator = ctx.expressioncomparisonoperator();
			if (expressioncomparisonoperator == null) {
				throw new UnsupportedOperationException("Only the expressionComparisonOperator is supported, not the numericComparisonOperator or the stringComparisonOperator.");
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
