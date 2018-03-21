package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.function.Function;

public class RefinementBuilderImpl implements RefinementBuilder {

	private final BoolQueryBuilder query;
	private final String path;
	private final QueryBuilder branchCriteria;
	private final boolean stated;
	private final QueryService queryService;
	private Function<QueryConcept, Boolean> inclusionFilter;

	public RefinementBuilderImpl(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		this.query = query;
		this.path = path;
		this.branchCriteria = branchCriteria;
		this.stated = stated;
		this.queryService = queryService;
	}

	public BoolQueryBuilder getQuery() {
		return query;
	}

	@Override
	public void setInclusionFilter(Function<QueryConcept, Boolean> inclusionFilter) {
		this.inclusionFilter = inclusionFilter;
	}

	@Override
	public Function<QueryConcept, Boolean> getInclusionFilter() {
		return inclusionFilter;
	}

	public String getPath() {
		return path;
	}

	public QueryBuilder getBranchCriteria() {
		return branchCriteria;
	}

	public boolean isStated() {
		return stated;
	}

	public QueryService getQueryService() {
		return queryService;
	}
}
