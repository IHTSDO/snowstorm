package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeGroup;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SRefinement;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.MatchContext;

import java.util.Set;

public class SEclAttributeGroup extends EclAttributeGroup implements SRefinement {

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		// All grouping checks require the inclusion filter because it's not supported by the index
		refinementBuilder.inclusionFilterRequired();

		BoolQueryBuilder attributesQueryForSingleGroup = new BoolQueryBuilder();
		((SEclAttributeSet)attributeSet).addCriteria(new SubRefinementBuilder(refinementBuilder, attributesQueryForSingleGroup));
	}

	@Override
	public Set<String> getConceptIds() {
		return ((SEclAttributeSet) attributeSet).getConceptIds();
	}

	boolean isMatch(MatchContext matchContext) {
		MatchContext groupMatchContext = new MatchContext(matchContext, true);
		((SEclAttributeSet) attributeSet).isMatch(groupMatchContext);

		Set<Integer> matchingGroups = groupMatchContext.getMatchingGroups();
		return ((cardinalityMin == null && matchingGroups.size() > 0) || (cardinalityMin != null && cardinalityMin <= matchingGroups.size()))
				&& (cardinalityMax == null || cardinalityMax >= matchingGroups.size());
	}
}
