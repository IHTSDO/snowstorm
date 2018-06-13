package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.function.Function;

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
	public Function<QueryConcept, Boolean> getInclusionFilter() {
		return refinementBuilder.getInclusionFilter();
	}

	@Override
	public void setInclusionFilter(Function<QueryConcept, Boolean> inclusionFilter) {
		refinementBuilder.setInclusionFilter(inclusionFilter);
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

	@Override
	public void inclusionFilterRequired() {
		refinementBuilder.inclusionFilterRequired();
	}

	@Override
	public boolean isInclusionFilterRequired() {
		return refinementBuilder.isInclusionFilterRequired();
	}
}
