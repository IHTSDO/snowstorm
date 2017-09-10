package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

public class EclAttributeSet implements Refinement {

	private SubAttributeSet subAttributeSet;
//	private List<SubAttributeSet> conjunctionAttributeSets;
//	private List<SubAttributeSet> disjunctionAttributeSets;

	public EclAttributeSet() {
//		conjunctionAttributeSets = new ArrayList<>();
//		disjunctionAttributeSets = new ArrayList<>();
	}

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		subAttributeSet.addCriteria(query, path, branchCriteria, stated, queryService);
	}

	public void setSubAttributeSet(SubAttributeSet subAttributeSet) {
		this.subAttributeSet = subAttributeSet;
	}

	public SubAttributeSet getSubAttributeSet() {
		return subAttributeSet;
	}
//
//	public void addConjunctionAttributeSet(SubAttributeSet subAttributeSet) {
//		conjunctionAttributeSets.add(subAttributeSet);
//	}
//
//	public void addDisjunctionAttributeSet(SubAttributeSet subAttributeSet) {
//		disjunctionAttributeSets.add(subAttributeSet);
//	}
}
