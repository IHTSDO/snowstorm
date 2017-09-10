package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

import java.util.ArrayList;
import java.util.List;

public class EclRefinement implements Refinement {

	private SubRefinement subRefinement;
	private List<SubRefinement> conjunctionSubRefinements;

	public EclRefinement() {
		conjunctionSubRefinements = new ArrayList<>();
	}

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		EclAttribute attribute = getSubRefinement().getEclAttributeSet().getSubAttributeSet().getAttribute();
		attribute.addCriteria(query, path, branchCriteria, stated, queryService);
		for (SubRefinement conjunctionSubRefinement : conjunctionSubRefinements) {
			conjunctionSubRefinement.addCriteria(query, path, branchCriteria, stated, queryService);
		}
	}

	public void setSubRefinement(SubRefinement subRefinement) {
		this.subRefinement = subRefinement;
	}

	public SubRefinement getSubRefinement() {
		return subRefinement;
	}

	public List<SubRefinement> getConjunctionSubRefinements() {
		return conjunctionSubRefinements;
	}

	public void setConjunctionSubRefinements(List<SubRefinement> conjunctionSubRefinements) {
		this.conjunctionSubRefinements = conjunctionSubRefinements;
	}
}
