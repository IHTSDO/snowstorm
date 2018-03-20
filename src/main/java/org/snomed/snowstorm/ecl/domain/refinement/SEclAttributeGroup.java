package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeGroup;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;

public class SEclAttributeGroup extends EclAttributeGroup implements SRefinement {

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		// The index can not support this kind of query directly so we need to do something special here:
		// 1. Grab concepts with matching attribute types and values (regardless of group)
		// 2. Filter the results by processing the attribute group map (which is stored as a plain string in the index)
		// 3. Add the matching concepts to a filter on the parent query

		BoolQueryBuilder attributesQueryForSingleGroup = new BoolQueryBuilder();
		((SEclAttributeSet)attributeSet).addCriteria(new SubRefinementBuilder(refinementBuilder, attributesQueryForSingleGroup));
	}

}
