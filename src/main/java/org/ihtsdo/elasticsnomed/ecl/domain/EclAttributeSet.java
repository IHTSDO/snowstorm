package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class EclAttributeSet implements Refinement {

	private SubAttributeSet subAttributeSet;
	private List<SubAttributeSet> conjunctionAttributeSet;
	private List<SubAttributeSet> disjunctionAttributeSet;

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		// In Elasticsearch disjunction (OR) clauses are written by adding a 'must' clause and appending 'should' clauses to that.
		// The first two types of refinements have to be part of the first 'should' query because they may be the
		// first half of a disjunction clause.

		BoolQueryBuilder shouldQueries = boolQuery();
		query.must(shouldQueries);
		BoolQueryBuilder firstShouldQuery = boolQuery();
		shouldQueries.should(firstShouldQuery);

		subAttributeSet.addCriteria(firstShouldQuery, path, branchCriteria, stated, queryService);
		if (conjunctionAttributeSet != null) {
			for (SubAttributeSet attributeSet : conjunctionAttributeSet) {
				attributeSet.addCriteria(firstShouldQuery, path, branchCriteria, stated, queryService);
			}
		}
		if (disjunctionAttributeSet != null && !disjunctionAttributeSet.isEmpty()) {
			for (SubAttributeSet attributeSet : disjunctionAttributeSet) {
				BoolQueryBuilder additionalShouldQuery = boolQuery();
				shouldQueries.should(additionalShouldQuery);
				attributeSet.addCriteria(additionalShouldQuery, path, branchCriteria, stated, queryService);
			}
		}
	}

	public void setSubAttributeSet(SubAttributeSet subAttributeSet) {
		this.subAttributeSet = subAttributeSet;
	}

	public SubAttributeSet getSubAttributeSet() {
		return subAttributeSet;
	}

	public void setConjunctionAttributeSet(List<SubAttributeSet> conjunctionAttributeSet) {
		this.conjunctionAttributeSet = conjunctionAttributeSet;
	}

	public void setDisjunctionAttributeSet(List<SubAttributeSet> disjunctionAttributeSet) {
		this.disjunctionAttributeSet = disjunctionAttributeSet;
	}

}
