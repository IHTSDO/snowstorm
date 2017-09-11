package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

public class SubRefinement implements Refinement {

	private EclAttributeSet eclAttributeSet;
	private EclRefinement eclRefinement;

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		if (eclAttributeSet != null) {
			eclAttributeSet.addCriteria(query, path, branchCriteria, stated, queryService);
		} else {
			eclRefinement.addCriteria(query, path, branchCriteria, stated, queryService);
		}
	}

	public void setEclAttributeSet(EclAttributeSet eclAttributeSet) {
		this.eclAttributeSet = eclAttributeSet;
	}

	public EclAttributeSet getEclAttributeSet() {
		return eclAttributeSet;
	}

	public void setEclRefinement(EclRefinement eclRefinement) {
		this.eclRefinement = eclRefinement;
	}

}
