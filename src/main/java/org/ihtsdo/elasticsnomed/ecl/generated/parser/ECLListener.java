// Generated from ECL.txt by ANTLR 4.5.3
package org.ihtsdo.elasticsnomed.ecl.generated.parser;

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link ECLParser}.
 */
public interface ECLListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link ECLParser#expressionconstraint}.
	 * @param ctx the parse tree
	 */
	void enterExpressionconstraint(ECLParser.ExpressionconstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#expressionconstraint}.
	 * @param ctx the parse tree
	 */
	void exitExpressionconstraint(ECLParser.ExpressionconstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#refinedexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void enterRefinedexpressionconstraint(ECLParser.RefinedexpressionconstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#refinedexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void exitRefinedexpressionconstraint(ECLParser.RefinedexpressionconstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#compoundexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void enterCompoundexpressionconstraint(ECLParser.CompoundexpressionconstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#compoundexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void exitCompoundexpressionconstraint(ECLParser.CompoundexpressionconstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#conjunctionexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void enterConjunctionexpressionconstraint(ECLParser.ConjunctionexpressionconstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#conjunctionexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void exitConjunctionexpressionconstraint(ECLParser.ConjunctionexpressionconstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#disjunctionexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void enterDisjunctionexpressionconstraint(ECLParser.DisjunctionexpressionconstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#disjunctionexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void exitDisjunctionexpressionconstraint(ECLParser.DisjunctionexpressionconstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#exclusionexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void enterExclusionexpressionconstraint(ECLParser.ExclusionexpressionconstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#exclusionexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void exitExclusionexpressionconstraint(ECLParser.ExclusionexpressionconstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#dottedexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void enterDottedexpressionconstraint(ECLParser.DottedexpressionconstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#dottedexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void exitDottedexpressionconstraint(ECLParser.DottedexpressionconstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#dottedexpressionattribute}.
	 * @param ctx the parse tree
	 */
	void enterDottedexpressionattribute(ECLParser.DottedexpressionattributeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#dottedexpressionattribute}.
	 * @param ctx the parse tree
	 */
	void exitDottedexpressionattribute(ECLParser.DottedexpressionattributeContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#subexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void enterSubexpressionconstraint(ECLParser.SubexpressionconstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#subexpressionconstraint}.
	 * @param ctx the parse tree
	 */
	void exitSubexpressionconstraint(ECLParser.SubexpressionconstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#eclfocusconcept}.
	 * @param ctx the parse tree
	 */
	void enterEclfocusconcept(ECLParser.EclfocusconceptContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#eclfocusconcept}.
	 * @param ctx the parse tree
	 */
	void exitEclfocusconcept(ECLParser.EclfocusconceptContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#dot}.
	 * @param ctx the parse tree
	 */
	void enterDot(ECLParser.DotContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#dot}.
	 * @param ctx the parse tree
	 */
	void exitDot(ECLParser.DotContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#memberof}.
	 * @param ctx the parse tree
	 */
	void enterMemberof(ECLParser.MemberofContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#memberof}.
	 * @param ctx the parse tree
	 */
	void exitMemberof(ECLParser.MemberofContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#eclconceptreference}.
	 * @param ctx the parse tree
	 */
	void enterEclconceptreference(ECLParser.EclconceptreferenceContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#eclconceptreference}.
	 * @param ctx the parse tree
	 */
	void exitEclconceptreference(ECLParser.EclconceptreferenceContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#conceptid}.
	 * @param ctx the parse tree
	 */
	void enterConceptid(ECLParser.ConceptidContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#conceptid}.
	 * @param ctx the parse tree
	 */
	void exitConceptid(ECLParser.ConceptidContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#term}.
	 * @param ctx the parse tree
	 */
	void enterTerm(ECLParser.TermContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#term}.
	 * @param ctx the parse tree
	 */
	void exitTerm(ECLParser.TermContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#wildcard}.
	 * @param ctx the parse tree
	 */
	void enterWildcard(ECLParser.WildcardContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#wildcard}.
	 * @param ctx the parse tree
	 */
	void exitWildcard(ECLParser.WildcardContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#constraintoperator}.
	 * @param ctx the parse tree
	 */
	void enterConstraintoperator(ECLParser.ConstraintoperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#constraintoperator}.
	 * @param ctx the parse tree
	 */
	void exitConstraintoperator(ECLParser.ConstraintoperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#descendantof}.
	 * @param ctx the parse tree
	 */
	void enterDescendantof(ECLParser.DescendantofContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#descendantof}.
	 * @param ctx the parse tree
	 */
	void exitDescendantof(ECLParser.DescendantofContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#descendantorselfof}.
	 * @param ctx the parse tree
	 */
	void enterDescendantorselfof(ECLParser.DescendantorselfofContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#descendantorselfof}.
	 * @param ctx the parse tree
	 */
	void exitDescendantorselfof(ECLParser.DescendantorselfofContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#childof}.
	 * @param ctx the parse tree
	 */
	void enterChildof(ECLParser.ChildofContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#childof}.
	 * @param ctx the parse tree
	 */
	void exitChildof(ECLParser.ChildofContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#ancestorof}.
	 * @param ctx the parse tree
	 */
	void enterAncestorof(ECLParser.AncestorofContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#ancestorof}.
	 * @param ctx the parse tree
	 */
	void exitAncestorof(ECLParser.AncestorofContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#ancestororselfof}.
	 * @param ctx the parse tree
	 */
	void enterAncestororselfof(ECLParser.AncestororselfofContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#ancestororselfof}.
	 * @param ctx the parse tree
	 */
	void exitAncestororselfof(ECLParser.AncestororselfofContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#parentof}.
	 * @param ctx the parse tree
	 */
	void enterParentof(ECLParser.ParentofContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#parentof}.
	 * @param ctx the parse tree
	 */
	void exitParentof(ECLParser.ParentofContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#conjunction}.
	 * @param ctx the parse tree
	 */
	void enterConjunction(ECLParser.ConjunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#conjunction}.
	 * @param ctx the parse tree
	 */
	void exitConjunction(ECLParser.ConjunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#disjunction}.
	 * @param ctx the parse tree
	 */
	void enterDisjunction(ECLParser.DisjunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#disjunction}.
	 * @param ctx the parse tree
	 */
	void exitDisjunction(ECLParser.DisjunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#exclusion}.
	 * @param ctx the parse tree
	 */
	void enterExclusion(ECLParser.ExclusionContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#exclusion}.
	 * @param ctx the parse tree
	 */
	void exitExclusion(ECLParser.ExclusionContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#eclrefinement}.
	 * @param ctx the parse tree
	 */
	void enterEclrefinement(ECLParser.EclrefinementContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#eclrefinement}.
	 * @param ctx the parse tree
	 */
	void exitEclrefinement(ECLParser.EclrefinementContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#conjunctionrefinementset}.
	 * @param ctx the parse tree
	 */
	void enterConjunctionrefinementset(ECLParser.ConjunctionrefinementsetContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#conjunctionrefinementset}.
	 * @param ctx the parse tree
	 */
	void exitConjunctionrefinementset(ECLParser.ConjunctionrefinementsetContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#disjunctionrefinementset}.
	 * @param ctx the parse tree
	 */
	void enterDisjunctionrefinementset(ECLParser.DisjunctionrefinementsetContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#disjunctionrefinementset}.
	 * @param ctx the parse tree
	 */
	void exitDisjunctionrefinementset(ECLParser.DisjunctionrefinementsetContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#subrefinement}.
	 * @param ctx the parse tree
	 */
	void enterSubrefinement(ECLParser.SubrefinementContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#subrefinement}.
	 * @param ctx the parse tree
	 */
	void exitSubrefinement(ECLParser.SubrefinementContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#eclattributeset}.
	 * @param ctx the parse tree
	 */
	void enterEclattributeset(ECLParser.EclattributesetContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#eclattributeset}.
	 * @param ctx the parse tree
	 */
	void exitEclattributeset(ECLParser.EclattributesetContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#conjunctionattributeset}.
	 * @param ctx the parse tree
	 */
	void enterConjunctionattributeset(ECLParser.ConjunctionattributesetContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#conjunctionattributeset}.
	 * @param ctx the parse tree
	 */
	void exitConjunctionattributeset(ECLParser.ConjunctionattributesetContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#disjunctionattributeset}.
	 * @param ctx the parse tree
	 */
	void enterDisjunctionattributeset(ECLParser.DisjunctionattributesetContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#disjunctionattributeset}.
	 * @param ctx the parse tree
	 */
	void exitDisjunctionattributeset(ECLParser.DisjunctionattributesetContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#subattributeset}.
	 * @param ctx the parse tree
	 */
	void enterSubattributeset(ECLParser.SubattributesetContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#subattributeset}.
	 * @param ctx the parse tree
	 */
	void exitSubattributeset(ECLParser.SubattributesetContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#eclattributegroup}.
	 * @param ctx the parse tree
	 */
	void enterEclattributegroup(ECLParser.EclattributegroupContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#eclattributegroup}.
	 * @param ctx the parse tree
	 */
	void exitEclattributegroup(ECLParser.EclattributegroupContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#eclattribute}.
	 * @param ctx the parse tree
	 */
	void enterEclattribute(ECLParser.EclattributeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#eclattribute}.
	 * @param ctx the parse tree
	 */
	void exitEclattribute(ECLParser.EclattributeContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#cardinality}.
	 * @param ctx the parse tree
	 */
	void enterCardinality(ECLParser.CardinalityContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#cardinality}.
	 * @param ctx the parse tree
	 */
	void exitCardinality(ECLParser.CardinalityContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#minvalue}.
	 * @param ctx the parse tree
	 */
	void enterMinvalue(ECLParser.MinvalueContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#minvalue}.
	 * @param ctx the parse tree
	 */
	void exitMinvalue(ECLParser.MinvalueContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#to}.
	 * @param ctx the parse tree
	 */
	void enterTo(ECLParser.ToContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#to}.
	 * @param ctx the parse tree
	 */
	void exitTo(ECLParser.ToContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#maxvalue}.
	 * @param ctx the parse tree
	 */
	void enterMaxvalue(ECLParser.MaxvalueContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#maxvalue}.
	 * @param ctx the parse tree
	 */
	void exitMaxvalue(ECLParser.MaxvalueContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#many}.
	 * @param ctx the parse tree
	 */
	void enterMany(ECLParser.ManyContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#many}.
	 * @param ctx the parse tree
	 */
	void exitMany(ECLParser.ManyContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#reverseflag}.
	 * @param ctx the parse tree
	 */
	void enterReverseflag(ECLParser.ReverseflagContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#reverseflag}.
	 * @param ctx the parse tree
	 */
	void exitReverseflag(ECLParser.ReverseflagContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#eclattributename}.
	 * @param ctx the parse tree
	 */
	void enterEclattributename(ECLParser.EclattributenameContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#eclattributename}.
	 * @param ctx the parse tree
	 */
	void exitEclattributename(ECLParser.EclattributenameContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#expressioncomparisonoperator}.
	 * @param ctx the parse tree
	 */
	void enterExpressioncomparisonoperator(ECLParser.ExpressioncomparisonoperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#expressioncomparisonoperator}.
	 * @param ctx the parse tree
	 */
	void exitExpressioncomparisonoperator(ECLParser.ExpressioncomparisonoperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#numericcomparisonoperator}.
	 * @param ctx the parse tree
	 */
	void enterNumericcomparisonoperator(ECLParser.NumericcomparisonoperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#numericcomparisonoperator}.
	 * @param ctx the parse tree
	 */
	void exitNumericcomparisonoperator(ECLParser.NumericcomparisonoperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#stringcomparisonoperator}.
	 * @param ctx the parse tree
	 */
	void enterStringcomparisonoperator(ECLParser.StringcomparisonoperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#stringcomparisonoperator}.
	 * @param ctx the parse tree
	 */
	void exitStringcomparisonoperator(ECLParser.StringcomparisonoperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#numericvalue}.
	 * @param ctx the parse tree
	 */
	void enterNumericvalue(ECLParser.NumericvalueContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#numericvalue}.
	 * @param ctx the parse tree
	 */
	void exitNumericvalue(ECLParser.NumericvalueContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#stringvalue}.
	 * @param ctx the parse tree
	 */
	void enterStringvalue(ECLParser.StringvalueContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#stringvalue}.
	 * @param ctx the parse tree
	 */
	void exitStringvalue(ECLParser.StringvalueContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#integervalue}.
	 * @param ctx the parse tree
	 */
	void enterIntegervalue(ECLParser.IntegervalueContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#integervalue}.
	 * @param ctx the parse tree
	 */
	void exitIntegervalue(ECLParser.IntegervalueContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#decimalvalue}.
	 * @param ctx the parse tree
	 */
	void enterDecimalvalue(ECLParser.DecimalvalueContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#decimalvalue}.
	 * @param ctx the parse tree
	 */
	void exitDecimalvalue(ECLParser.DecimalvalueContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#nonnegativeintegervalue}.
	 * @param ctx the parse tree
	 */
	void enterNonnegativeintegervalue(ECLParser.NonnegativeintegervalueContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#nonnegativeintegervalue}.
	 * @param ctx the parse tree
	 */
	void exitNonnegativeintegervalue(ECLParser.NonnegativeintegervalueContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#sctid}.
	 * @param ctx the parse tree
	 */
	void enterSctid(ECLParser.SctidContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#sctid}.
	 * @param ctx the parse tree
	 */
	void exitSctid(ECLParser.SctidContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#ws}.
	 * @param ctx the parse tree
	 */
	void enterWs(ECLParser.WsContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#ws}.
	 * @param ctx the parse tree
	 */
	void exitWs(ECLParser.WsContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#mws}.
	 * @param ctx the parse tree
	 */
	void enterMws(ECLParser.MwsContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#mws}.
	 * @param ctx the parse tree
	 */
	void exitMws(ECLParser.MwsContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#comment}.
	 * @param ctx the parse tree
	 */
	void enterComment(ECLParser.CommentContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#comment}.
	 * @param ctx the parse tree
	 */
	void exitComment(ECLParser.CommentContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#nonstarchar}.
	 * @param ctx the parse tree
	 */
	void enterNonstarchar(ECLParser.NonstarcharContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#nonstarchar}.
	 * @param ctx the parse tree
	 */
	void exitNonstarchar(ECLParser.NonstarcharContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#starwithnonfslash}.
	 * @param ctx the parse tree
	 */
	void enterStarwithnonfslash(ECLParser.StarwithnonfslashContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#starwithnonfslash}.
	 * @param ctx the parse tree
	 */
	void exitStarwithnonfslash(ECLParser.StarwithnonfslashContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#nonfslash}.
	 * @param ctx the parse tree
	 */
	void enterNonfslash(ECLParser.NonfslashContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#nonfslash}.
	 * @param ctx the parse tree
	 */
	void exitNonfslash(ECLParser.NonfslashContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#sp}.
	 * @param ctx the parse tree
	 */
	void enterSp(ECLParser.SpContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#sp}.
	 * @param ctx the parse tree
	 */
	void exitSp(ECLParser.SpContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#htab}.
	 * @param ctx the parse tree
	 */
	void enterHtab(ECLParser.HtabContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#htab}.
	 * @param ctx the parse tree
	 */
	void exitHtab(ECLParser.HtabContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#cr}.
	 * @param ctx the parse tree
	 */
	void enterCr(ECLParser.CrContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#cr}.
	 * @param ctx the parse tree
	 */
	void exitCr(ECLParser.CrContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#lf}.
	 * @param ctx the parse tree
	 */
	void enterLf(ECLParser.LfContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#lf}.
	 * @param ctx the parse tree
	 */
	void exitLf(ECLParser.LfContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#qm}.
	 * @param ctx the parse tree
	 */
	void enterQm(ECLParser.QmContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#qm}.
	 * @param ctx the parse tree
	 */
	void exitQm(ECLParser.QmContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#bs}.
	 * @param ctx the parse tree
	 */
	void enterBs(ECLParser.BsContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#bs}.
	 * @param ctx the parse tree
	 */
	void exitBs(ECLParser.BsContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#digit}.
	 * @param ctx the parse tree
	 */
	void enterDigit(ECLParser.DigitContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#digit}.
	 * @param ctx the parse tree
	 */
	void exitDigit(ECLParser.DigitContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#zero}.
	 * @param ctx the parse tree
	 */
	void enterZero(ECLParser.ZeroContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#zero}.
	 * @param ctx the parse tree
	 */
	void exitZero(ECLParser.ZeroContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#digitnonzero}.
	 * @param ctx the parse tree
	 */
	void enterDigitnonzero(ECLParser.DigitnonzeroContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#digitnonzero}.
	 * @param ctx the parse tree
	 */
	void exitDigitnonzero(ECLParser.DigitnonzeroContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#nonwsnonpipe}.
	 * @param ctx the parse tree
	 */
	void enterNonwsnonpipe(ECLParser.NonwsnonpipeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#nonwsnonpipe}.
	 * @param ctx the parse tree
	 */
	void exitNonwsnonpipe(ECLParser.NonwsnonpipeContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#anynonescapedchar}.
	 * @param ctx the parse tree
	 */
	void enterAnynonescapedchar(ECLParser.AnynonescapedcharContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#anynonescapedchar}.
	 * @param ctx the parse tree
	 */
	void exitAnynonescapedchar(ECLParser.AnynonescapedcharContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#escapedchar}.
	 * @param ctx the parse tree
	 */
	void enterEscapedchar(ECLParser.EscapedcharContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#escapedchar}.
	 * @param ctx the parse tree
	 */
	void exitEscapedchar(ECLParser.EscapedcharContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#utf8_2}.
	 * @param ctx the parse tree
	 */
	void enterUtf8_2(ECLParser.Utf8_2Context ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#utf8_2}.
	 * @param ctx the parse tree
	 */
	void exitUtf8_2(ECLParser.Utf8_2Context ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#utf8_3}.
	 * @param ctx the parse tree
	 */
	void enterUtf8_3(ECLParser.Utf8_3Context ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#utf8_3}.
	 * @param ctx the parse tree
	 */
	void exitUtf8_3(ECLParser.Utf8_3Context ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#utf8_4}.
	 * @param ctx the parse tree
	 */
	void enterUtf8_4(ECLParser.Utf8_4Context ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#utf8_4}.
	 * @param ctx the parse tree
	 */
	void exitUtf8_4(ECLParser.Utf8_4Context ctx);
	/**
	 * Enter a parse tree produced by {@link ECLParser#utf8_tail}.
	 * @param ctx the parse tree
	 */
	void enterUtf8_tail(ECLParser.Utf8_tailContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECLParser#utf8_tail}.
	 * @param ctx the parse tree
	 */
	void exitUtf8_tail(ECLParser.Utf8_tailContext ctx);
}