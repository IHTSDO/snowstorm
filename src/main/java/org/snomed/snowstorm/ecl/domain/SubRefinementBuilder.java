package org.snomed.snowstorm.ecl.domain;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.kaicode.elasticvc.api.BranchCriteria;

import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.ecl.ECLContentService;

import java.util.function.Function;

public class SubRefinementBuilder implements RefinementBuilder {

	private final RefinementBuilder refinementBuilder;
	private final BoolQuery.Builder query;
	private Boolean shouldReturnPrefetchedResults;

	public SubRefinementBuilder(RefinementBuilder refinementBuilder, BoolQuery.Builder queryBuilder) {
		this.refinementBuilder = refinementBuilder;
		this.query = queryBuilder;
	}

	@Override
	public BoolQuery.Builder getQueryBuilder() {
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
	public BranchCriteria getBranchCriteria() {
		return refinementBuilder.getBranchCriteria();
	}

	@Override
	public boolean isStated() {
		return refinementBuilder.isStated();
	}

	@Override
	public ECLContentService getEclContentService() {
		return refinementBuilder.getEclContentService();
	}

	@Override
	public void inclusionFilterRequired() {
		refinementBuilder.inclusionFilterRequired();
	}

	@Override
	public boolean isInclusionFilterRequired() {
		return refinementBuilder.isInclusionFilterRequired();
	}

	@Override
	public void setShouldPrefetchMemberOfQueryResults(boolean shouldReturnPrefetchedResults) {
		this.shouldReturnPrefetchedResults = shouldReturnPrefetchedResults;
	}
	@Override
	public Boolean shouldPrefetchMemberOfQueryResults() {
		return this.shouldReturnPrefetchedResults;
	}
}
