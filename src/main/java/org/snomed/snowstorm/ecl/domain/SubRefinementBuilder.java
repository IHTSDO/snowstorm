package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;

public class SubRefinementBuilder implements RefinementBuilder {

	private final RefinementBuilder refinementBuilder;
	private final BoolQueryBuilder query;

	public SubRefinementBuilder(RefinementBuilder refinementBuilder, BoolQueryBuilder query) {
		this.refinementBuilder = refinementBuilder;
		this.query = query;
	}

	@Override
	public BoolQueryBuilder getQuery() {
		return query;
	}

	@Override
	public String getPath() {
		return refinementBuilder.getPath();
	}

	@Override
	public QueryBuilder getBranchCriteria() {
		return refinementBuilder.getBranchCriteria();
	}

	@Override
	public boolean isStated() {
		return refinementBuilder.isStated();
	}

	@Override
	public QueryService getQueryService() {
		return refinementBuilder.getQueryService();
	}
}
