package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;

import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class EclAttributeSet implements Refinement {

	private EclAttributeGroup parentGroup;
	private SubAttributeSet subAttributeSet;
	private List<SubAttributeSet> conjunctionAttributeSet;
	private List<SubAttributeSet> disjunctionAttributeSet;

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
		subAttributeSet.addCriteria(firstShouldRefinementBuilder);
		if (conjunctionAttributeSet != null) {
			for (SubAttributeSet attributeSet : conjunctionAttributeSet) {
				attributeSet.addCriteria(firstShouldRefinementBuilder);
			}
		}
		if (disjunctionAttributeSet != null && !disjunctionAttributeSet.isEmpty()) {
			for (SubAttributeSet attributeSet : disjunctionAttributeSet) {
				BoolQueryBuilder additionalShouldQuery = boolQuery();
				shouldQueries.should(additionalShouldQuery);
				attributeSet.addCriteria(new SubRefinementBuilder(refinementBuilder, additionalShouldQuery));
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

	@Override
	public String toString() {
		return "EclAttributeSet{" +
				"withinGroup=" + (parentGroup != null) +
				", subAttributeSet=" + subAttributeSet +
				", conjunctionAttributeSet=" + conjunctionAttributeSet +
				", disjunctionAttributeSet=" + disjunctionAttributeSet +
				'}';
	}

	public void setParentGroup(EclAttributeGroup parentGroup) {
		this.parentGroup = parentGroup;
	}

}
