package org.snomed.snowstorm.ecl.domain;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.ecl.ECLContentService;

import java.util.function.Function;

public class RefinementBuilderImpl implements RefinementBuilder {

	private final BoolQueryBuilder query;
	private final String path;
	private final BranchCriteria branchCriteria;
	private final boolean stated;
	private final ECLContentService eclContentService;
	private Function<QueryConcept, Boolean> inclusionFilter;
	private boolean inclusionFilterRequired;

	public RefinementBuilderImpl(BoolQueryBuilder query, String path, BranchCriteria branchCriteria, boolean stated, ECLContentService eclContentService) {
		this.query = query;
		this.path = path;
		this.branchCriteria = branchCriteria;
		this.stated = stated;
		this.eclContentService = eclContentService;
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
}
