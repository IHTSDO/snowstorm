package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

import java.util.Collection;

public interface ConceptSelector {

	Collection<Long> select(QueryBuilder branchCriteria, QueryService queryService, String path, boolean stated);

}
