package org.ihtsdo.elasticsnomed.ecl;

import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.ihtsdo.elasticsnomed.ecl.domain.ExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class ECLQueryService {

	@Autowired
	private ECLQueryBuilder queryBuilder;

	@Autowired
	private QueryService queryService;

	public Collection<Long> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, null, null);
	}

	public Collection<Long> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated, List<Long> conceptIdFilter, PageRequest pageRequest) throws ECLException {
		ExpressionConstraint expressionConstraint = queryBuilder.createQuery(ecl);
		return expressionConstraint.select(path, branchCriteria, stated, queryService, conceptIdFilter, pageRequest);
	}

}
