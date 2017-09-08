package org.ihtsdo.elasticsnomed.ecl;

import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.ihtsdo.elasticsnomed.ecl.domain.ExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class ECLQueryService {

	@Autowired
	private ECLQueryBuilder queryBuilder;

	@Autowired
	private QueryService queryService;

	public Collection<Long> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated) throws ECLException {
		ExpressionConstraint expressionConstraint = queryBuilder.createQuery(ecl);
		return expressionConstraint.select(path, branchCriteria, stated, queryService);
	}

}
