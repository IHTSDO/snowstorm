package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

public class SubAttributeSet implements Refinement {

	private EclAttribute attribute;

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		attribute.addCriteria(query, path, branchCriteria, stated, queryService);
	}

	public void setAttribute(EclAttribute attribute) {
		this.attribute = attribute;
	}

	public EclAttribute getAttribute() {
		return attribute;
	}
}
