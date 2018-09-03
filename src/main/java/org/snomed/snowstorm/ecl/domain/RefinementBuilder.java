package org.snomed.snowstorm.ecl.domain;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.function.Function;

public interface RefinementBuilder {

	BoolQueryBuilder getQuery();

	Function<QueryConcept, Boolean> getInclusionFilter();

	void setInclusionFilter(Function<QueryConcept, Boolean> inclusionFilter);

	String getPath();

	BranchCriteria getBranchCriteria();

	boolean isStated();

	QueryService getQueryService();

	void inclusionFilterRequired();

	boolean isInclusionFilterRequired();
}
