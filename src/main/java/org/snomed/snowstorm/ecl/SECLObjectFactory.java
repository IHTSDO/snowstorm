package org.snomed.snowstorm.ecl;

import org.snomed.langauges.ecl.ECLObjectFactory;
import org.snomed.langauges.ecl.domain.ConceptReference;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.DottedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.*;
import org.snomed.langauges.ecl.domain.refinement.*;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SCompoundExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SDottedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SRefinedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.filter.*;
import org.snomed.snowstorm.ecl.domain.refinement.*;

import java.util.Set;

public class SECLObjectFactory extends ECLObjectFactory {

	@Override
	protected RefinedExpressionConstraint getRefinedExpressionConstraint(SubExpressionConstraint subExpressionConstraint, EclRefinement eclRefinement) {
		return new SRefinedExpressionConstraint(subExpressionConstraint, eclRefinement);
	}

	@Override
	protected CompoundExpressionConstraint getCompoundExpressionConstraint() {
		return new SCompoundExpressionConstraint();
	}

	@Override
	protected DottedExpressionConstraint getDottedExpressionConstraint(SubExpressionConstraint subExpressionConstraint) {
		return new SDottedExpressionConstraint(subExpressionConstraint);
	}

	@Override
	protected SubExpressionConstraint getSubExpressionConstraint(Operator operator) {
		return new SSubExpressionConstraint(operator);
	}

	@Override
	protected EclAttribute getAttribute() {
		return new SEclAttribute();
	}

	@Override
	protected EclAttributeGroup getAttributeGroup() {
		return new SEclAttributeGroup();
	}

	@Override
	protected EclAttributeSet getEclAttributeSet() {
		return new SEclAttributeSet();
	}

	@Override
	protected EclRefinement getRefinement() {
		return new SEclRefinement();
	}

	@Override
	protected SubAttributeSet getSubAttributeSet() {
		return new SSubAttributeSet();
	}

	@Override
	protected SubRefinement getSubRefinement() {
		return new SSubRefinement();
	}

	@Override
	public ConceptFilterConstraint getConceptFilterConstraint() {
		return new SConceptFilterConstraint();
	}

	@Override
	public FieldFilter getFieldFilter(String fieldName, boolean equals) {
		return new SFieldFilter(fieldName, equals);
	}

	@Override
	public EffectiveTimeFilter getEffectiveTimeFilter(NumericComparisonOperator operator, Set<Integer> effectiveTimes) {
		return new SEffectiveTimeFilter(operator, effectiveTimes);
	}

	@Override
	public DescriptionFilterConstraint getDescriptionFilterConstraint() {
		return new SDescriptionFilterConstraint();
	}

	@Override
	public TypedSearchTerm getTypedSearchTerm(SearchType searchType, String text) {
		return new STypedSearchTerm(searchType, text);
	}

	@Override
	public DialectFilter getDialectFilter(String text, boolean dialectAliasFilter) {
		return new SDialectFilter(text, dialectAliasFilter);
	}

	@Override
	public ActiveFilter getActiveFilter(boolean b) {
		return new SActiveFilter(b);
	}

	@Override
	public TermFilter getTermFilter(String text) {
		return new STermFilter(text);
	}

	@Override
	public DescriptionTypeFilter getDescriptionTypeFilter(String text) {
		return new SDescriptionTypeFilter(text);
	}

	@Override
	public LanguageFilter getLanguageFilter(String text) {
		return new SLanguageFilter(text);
	}

	@Override
	public DialectAcceptability getDialectAcceptability(ConceptReference conceptReference) {
		return new SDialectAcceptability(conceptReference);
	}

	@Override
	public DialectAcceptability getDialectAcceptability(SubExpressionConstraint expressionConstraint) {
		return new SDialectAcceptability(expressionConstraint);
	}

	@Override
	public DialectAcceptability getDialectAcceptability(String alias) {
		return new SDialectAcceptability(alias);
	}

	@Override
	public MemberFilterConstraint getMemberFilterConstraint() {
		return new SMemberFilterConstraint();
	}

	@Override
	public MemberFieldFilter getMemberFieldFilter(String fieldName) {
		return new SMemberFieldFilter(fieldName);
	}

	@Override
	public HistorySupplement getHistorySupplement() {
		return new SHistorySupplement();
	}
}
