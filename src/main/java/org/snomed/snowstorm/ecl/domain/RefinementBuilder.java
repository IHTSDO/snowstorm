package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;

public interface RefinementBuilder {

	BoolQueryBuilder getQuery();

	String getPath();

	QueryBuilder getBranchCriteria();

	boolean isStated();

	QueryService getQueryService();

}
