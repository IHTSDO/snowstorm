package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;

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
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		subRefinement.addCriteria(query, path, branchCriteria, stated, queryService);

		if (conjunctionSubRefinements != null) {
			for (SubRefinement conjunctionSubRefinement : conjunctionSubRefinements) {
				conjunctionSubRefinement.addCriteria(query, path, branchCriteria, stated, queryService);
			}
		}
		if (disjunctionSubRefinements != null && disjunctionSubRefinements.isEmpty()) {
			BoolQueryBuilder shouldQueries = boolQuery();
			query.must(shouldQueries);
			for (SubRefinement disjunctionSubRefinement : disjunctionSubRefinements) {
				BoolQueryBuilder shouldQuery = boolQuery();
				shouldQueries.should(shouldQuery);
				disjunctionSubRefinement.addCriteria(shouldQuery, path, branchCriteria, stated, queryService);
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

}
