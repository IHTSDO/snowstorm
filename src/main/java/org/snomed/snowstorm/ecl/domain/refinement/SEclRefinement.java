package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SRefinement;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.MatchContext;

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
		if (disjunctionSubRefinements != null && !disjunctionSubRefinements.isEmpty()) {
			BoolQueryBuilder shouldQueries = boolQuery();
			refinementBuilder.getQuery().must(shouldQueries);
			for (SubRefinement disjunctionSubRefinement : disjunctionSubRefinements) {
				BoolQueryBuilder shouldQuery = boolQuery();
				shouldQueries.should(shouldQuery);
				((SSubRefinement)disjunctionSubRefinement).addCriteria(new SubRefinementBuilder(refinementBuilder, shouldQuery));
			}
		}
	}

	public boolean isMatch(MatchContext matchContext) {
		boolean conceptMatches = ((SSubRefinement) subRefinement).isMatch(matchContext);
		if (conjunctionSubRefinements != null) {
			for (SubRefinement conjunctionSubRefinement : conjunctionSubRefinements) {
				conceptMatches = conceptMatches && ((SSubRefinement) conjunctionSubRefinement).isMatch(matchContext);
			}
		}
		if (disjunctionSubRefinements != null) {
			boolean oneMatches = false;
			for (SubRefinement disjunctionSubRefinement : disjunctionSubRefinements) {
				if (((SSubRefinement) disjunctionSubRefinement).isMatch(matchContext)) {
					oneMatches = true;
				}
			}
			conceptMatches = conceptMatches && oneMatches;
		}
		return conceptMatches;
	}
}
