package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SRefinement;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.MatchContext;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;

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
		Set<String> conceptIds = newHashSet();
		if (eclAttributeSet != null) {
			conceptIds.addAll(((SEclAttributeSet) eclAttributeSet).getConceptIds());
		}
		if (eclAttributeGroup != null) {
			conceptIds.addAll(((SEclAttributeGroup) eclAttributeGroup).getConceptIds());
		}
		if (eclRefinement != null) {
			conceptIds.addAll(((SEclRefinement) eclRefinement).getConceptIds());
		}
		return conceptIds;
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
}
