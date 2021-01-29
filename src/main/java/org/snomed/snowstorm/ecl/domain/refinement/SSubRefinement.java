package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SRefinement;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.MatchContext;

import java.util.Set;

public class SSubRefinement extends SubRefinement implements SRefinement {

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		if (eclAttributeSet != null) {
			((SEclAttributeSet)eclAttributeSet).addCriteria(refinementBuilder);
		} else if (eclAttributeGroup != null) {
			((SEclAttributeGroup)eclAttributeGroup).addCriteria(refinementBuilder);
		} else {
			((SEclRefinement)eclRefinement).addCriteria(refinementBuilder);
		}
	}

	@Override
	public Set<String> getConceptIds() {
		if (eclAttributeSet != null) {
			return ((SEclAttributeSet) eclAttributeSet).getConceptIds();
		} else if (eclAttributeGroup != null) {
			return ((SEclAttributeGroup) eclAttributeGroup).getConceptIds();
		} else {
			return ((SEclRefinement) eclRefinement).getConceptIds();
		}
	}

	boolean isMatch(MatchContext matchContext) {
		if (eclAttributeSet != null) {
			return ((SEclAttributeSet)eclAttributeSet).isMatch(matchContext.clear());
		} else if (eclAttributeGroup != null) {
			return ((SEclAttributeGroup)eclAttributeGroup).isMatch(matchContext.clear());
		} else {
			return ((SEclRefinement)eclRefinement).isMatch(matchContext.clear());
		}
	}

	public void toString(StringBuffer buffer) {
		if (eclAttributeSet != null) {
			((SEclAttributeSet) eclAttributeSet).toString(buffer);
		}
		if (eclAttributeGroup != null) {
			((SEclAttributeGroup) eclAttributeGroup).toString(buffer);
		}
		if (eclRefinement != null) {
			buffer.append("( ");
			((SEclRefinement) eclRefinement).toString(buffer);
			buffer.append(" )");
		}
	}
}
