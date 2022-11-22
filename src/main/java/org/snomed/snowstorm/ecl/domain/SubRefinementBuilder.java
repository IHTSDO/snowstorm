package org.snomed.snowstorm.ecl.domain;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.ecl.ECLContentService;

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
}
