package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class SEclRefinement extends EclRefinement implements SRefinement {

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		((SSubRefinement)subRefinement).addCriteria(refinementBuilder);

		if (conjunctionSubRefinements != null) {
			for (SubRefinement conjunctionSubRefinement : conjunctionSubRefinements) {
				((SSubRefinement)conjunctionSubRefinement).addCriteria(refinementBuilder);
			}
		}
		if (disjunctionSubRefinements != null && disjunctionSubRefinements.isEmpty()) {
			BoolQueryBuilder shouldQueries = boolQuery();
			refinementBuilder.getQuery().must(shouldQueries);
			for (SubRefinement disjunctionSubRefinement : disjunctionSubRefinements) {
				BoolQueryBuilder shouldQuery = boolQuery();
				shouldQueries.should(shouldQuery);
				((SSubRefinement)disjunctionSubRefinement).addCriteria(new SubRefinementBuilder(refinementBuilder, shouldQuery));
			}
		}
	}

}
