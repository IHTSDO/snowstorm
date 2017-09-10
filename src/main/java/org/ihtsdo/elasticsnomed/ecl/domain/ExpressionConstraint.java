package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;

public interface ExpressionConstraint extends Refinement {

	// Used to force no match
	String MISSING = "missing";
	Long MISSING_LONG = 111L;

	Set<Long> select(String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService);
	Set<Long> select(String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService, List<Long> conceptIdFilter, PageRequest pageRequest);

}
