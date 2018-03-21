package org.snomed.snowstorm.ecl;

import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
public class ECLQueryService {

	@Autowired
	private ECLQueryBuilder queryBuilder;

	@Autowired
	private QueryService queryService;

	Optional<Page<Long>> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, null, null);
	}

	public Optional<Page<Long>> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated, PageRequest pageRequest) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, null, pageRequest);
	}

	public Optional<Page<Long>> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated, Collection<Long> conceptIdFilter) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, conceptIdFilter, null);
	}

	public Optional<Page<Long>> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest) throws ECLException {
		SExpressionConstraint expressionConstraint = (SExpressionConstraint) queryBuilder.createQuery(ecl);
		return expressionConstraint.select(path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService);
	}


}
