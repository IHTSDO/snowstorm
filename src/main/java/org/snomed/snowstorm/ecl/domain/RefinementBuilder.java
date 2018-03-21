package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.function.Function;

public interface RefinementBuilder {

	BoolQueryBuilder getQuery();

	Function<QueryConcept, Boolean> getInclusionFilter();

	void setInclusionFilter(Function<QueryConcept, Boolean> inclusionFilter);

	String getPath();

	QueryBuilder getBranchCriteria();

	boolean isStated();

	QueryService getQueryService();

	void setInclusionFilterRequired(boolean bool);

	boolean isInclusionFilterRequired();
}
