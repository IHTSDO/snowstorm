package org.snomed.snowstorm.ecl.domain;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.kaicode.elasticvc.api.BranchCriteria;

import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.ecl.ECLContentService;

import java.util.function.Function;

public class RefinementBuilderImpl implements RefinementBuilder {

	private final BoolQuery.Builder queryBuilder;
	private final String path;
	private final BranchCriteria branchCriteria;
	private final boolean stated;
	private final ECLContentService eclContentService;
	private Function<QueryConcept, Boolean> inclusionFilter;
	private boolean inclusionFilterRequired;
	private Boolean shouldReturnPrefetchedResults;

	public RefinementBuilderImpl(BoolQuery.Builder queryBuilder, BranchCriteria branchCriteria, boolean stated, ECLContentService eclContentService) {
		this.queryBuilder = queryBuilder;
		this.path = branchCriteria.getBranchPath();
		this.branchCriteria = branchCriteria;
		this.stated = stated;
		this.eclContentService = eclContentService;
	}

	public BoolQuery.Builder getQueryBuilder() {
		return queryBuilder;
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

	public BranchCriteria getBranchCriteria() {
		return branchCriteria;
	}

	public boolean isStated() {
		return stated;
	}

	public ECLContentService getEclContentService() {
		return eclContentService;
	}

	@Override
	public void inclusionFilterRequired() {
		this.inclusionFilterRequired = true;
	}

	@Override
	public boolean isInclusionFilterRequired() {
		return inclusionFilterRequired;
	}

	@Override
	public void setShouldPrefetchMemberOfQueryResults(boolean prefetchMemberOfQueryResults) {
		this.shouldReturnPrefetchedResults = prefetchMemberOfQueryResults;
	}
	@Override
	public Boolean shouldPrefetchMemberOfQueryResults() {
		return this.shouldReturnPrefetchedResults;
	}
}
