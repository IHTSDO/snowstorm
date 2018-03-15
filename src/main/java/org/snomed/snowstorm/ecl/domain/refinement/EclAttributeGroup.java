package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;

import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class EclAttributeGroup implements Refinement {

	private EclAttributeSet attributeSet;
	private Integer cardinalityMin;
	private Integer cardinalityMax;

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		// The index can not support this kind of query directly so we need to do something special here:
		// 1. Grab concepts with matching attribute types and values (regardless of group)
		// 2. Filter the results by processing the attribute group map (which is stored as a plain string in the index)
		// 3. Add the matching concepts to a filter on the parent query

		BoolQueryBuilder attributesQueryForSingleGroup = new BoolQueryBuilder();
		attributeSet.addCriteria(new SubRefinementBuilder(refinementBuilder, attributesQueryForSingleGroup));
	}

	public void setAttributeSet(EclAttributeSet attributeSet) {
		this.attributeSet = attributeSet;
	}

	public void setCardinalityMin(int cardinalityMin) {
		this.cardinalityMin = cardinalityMin;
	}

	public Integer getCardinalityMin() {
		return cardinalityMin;
	}

	public void setCardinalityMax(int cardinalityMax) {
		this.cardinalityMax = cardinalityMax;
	}

	public Integer getCardinalityMax() {
		return cardinalityMax;
	}

	@Override
	public String toString() {
		return "EclAttributeGroup{" +
				"attributeSet=" + attributeSet +
				", cardinalityMin=" + cardinalityMin +
				", cardinalityMax=" + cardinalityMax +
				'}';
	}

}
