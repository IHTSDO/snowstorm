package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeSet;
import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class SEclAttributeSet extends EclAttributeSet implements SRefinement {

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		// In Elasticsearch disjunction (OR) clauses are written by adding a 'must' clause and appending 'should' clauses to that.
		// The first two types of refinements have to be part of the first 'should' query because they may be the
		// first half of a disjunction clause.

		BoolQueryBuilder shouldQueries = boolQuery();
		refinementBuilder.getQuery().must(shouldQueries);
		BoolQueryBuilder firstShouldQuery = boolQuery();
		shouldQueries.should(firstShouldQuery);

		SubRefinementBuilder firstShouldRefinementBuilder = new SubRefinementBuilder(refinementBuilder, firstShouldQuery);
		((SSubAttributeSet)subAttributeSet).addCriteria(firstShouldRefinementBuilder);
		if (conjunctionAttributeSet != null) {
			for (SubAttributeSet attributeSet : conjunctionAttributeSet) {
				((SSubAttributeSet)attributeSet).addCriteria(firstShouldRefinementBuilder);
			}
		}
		if (disjunctionAttributeSet != null && !disjunctionAttributeSet.isEmpty()) {
			for (SubAttributeSet attributeSet : disjunctionAttributeSet) {
				BoolQueryBuilder additionalShouldQuery = boolQuery();
				shouldQueries.should(additionalShouldQuery);
				((SSubAttributeSet)attributeSet).addCriteria(new SubRefinementBuilder(refinementBuilder, additionalShouldQuery));
			}
		}
	}

}
