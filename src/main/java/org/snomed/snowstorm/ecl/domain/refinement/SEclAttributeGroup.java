package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeGroup;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SRefinement;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.MatchContext;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class SEclAttributeGroup extends EclAttributeGroup implements SRefinement {

	public SEclAttributeGroup() {
		cardinalityMin = null;
		cardinalityMax = null;
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder, Consumer<List<Long>> filteredOrSupplementedContentCallback, boolean triedCache) {
		addCriteria(refinementBuilder);
	}

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

	public void setCardinalityMin(Integer cardinalityMin) {
		if (cardinalityMin != null) {
			super.setCardinalityMin(cardinalityMin);
		}
	}

	public void setCardinalityMax(Integer cardinalityMax) {
		if (cardinalityMax != null) {
			super.setCardinalityMax(cardinalityMax);
		}
	}

	public void toString(StringBuffer buffer) {
		SEclAttribute.cardinalityToString(getCardinalityMin(), getCardinalityMax(), buffer);
		buffer.append("{ ");
		((SEclAttributeSet) attributeSet).toString(buffer);
		buffer.append(" }");
	}
}
