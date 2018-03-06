package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.Collection;
import java.util.List;

public abstract class ExpressionConstraint implements Refinement {

	// Used to force no match
	static final String MISSING = "missing";
	static final Long MISSING_LONG = 111L;

	public List<Long> select(String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, QueryService queryService) {
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);
		addCriteria(query, path, branchCriteria, stated, queryService);
		return ConceptSelectorHelper.fetch(query, conceptIdFilter, queryService);
	}

}
