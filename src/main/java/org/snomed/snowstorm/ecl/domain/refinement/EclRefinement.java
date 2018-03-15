package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class EclRefinement implements Refinement {

	private SubRefinement subRefinement;
	private List<SubRefinement> conjunctionSubRefinements;
	private List<SubRefinement> disjunctionSubRefinements;

	public EclRefinement() {
		conjunctionSubRefinements = new ArrayList<>();
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		subRefinement.addCriteria(refinementBuilder);

		if (conjunctionSubRefinements != null) {
			for (SubRefinement conjunctionSubRefinement : conjunctionSubRefinements) {
				conjunctionSubRefinement.addCriteria(refinementBuilder);
			}
		}
		if (disjunctionSubRefinements != null && disjunctionSubRefinements.isEmpty()) {
			BoolQueryBuilder shouldQueries = boolQuery();
			refinementBuilder.getQuery().must(shouldQueries);
			for (SubRefinement disjunctionSubRefinement : disjunctionSubRefinements) {
				BoolQueryBuilder shouldQuery = boolQuery();
				shouldQueries.should(shouldQuery);
				disjunctionSubRefinement.addCriteria(new SubRefinementBuilder(refinementBuilder, shouldQuery));
			}
		}
	}

	public void setSubRefinement(SubRefinement subRefinement) {
		this.subRefinement = subRefinement;
	}

	public void setConjunctionSubRefinements(List<SubRefinement> conjunctionSubRefinements) {
		this.conjunctionSubRefinements = conjunctionSubRefinements;
	}

	public void setDisjunctionSubRefinements(List<SubRefinement> disjunctionSubRefinements) {
		this.disjunctionSubRefinements = disjunctionSubRefinements;
	}

	@Override
	public String toString() {
		return "EclRefinement{" +
				"subRefinement=" + subRefinement +
				", conjunctionSubRefinements=" + conjunctionSubRefinements +
				", disjunctionSubRefinements=" + disjunctionSubRefinements +
				'}';
	}
}
