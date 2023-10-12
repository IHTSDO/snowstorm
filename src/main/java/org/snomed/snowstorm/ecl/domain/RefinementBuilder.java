package org.snomed.snowstorm.ecl.domain;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.kaicode.elasticvc.api.BranchCriteria;

import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.ecl.ECLContentService;

import java.util.function.Function;

public interface RefinementBuilder {

	BoolQuery.Builder getQueryBuilder();

	Function<QueryConcept, Boolean> getInclusionFilter();

	void setInclusionFilter(Function<QueryConcept, Boolean> inclusionFilter);

	String getPath();

	BranchCriteria getBranchCriteria();

	boolean isStated();

	ECLContentService getEclContentService();

	void inclusionFilterRequired();

	boolean isInclusionFilterRequired();
}
