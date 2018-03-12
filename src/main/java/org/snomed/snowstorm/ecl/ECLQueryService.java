package org.snomed.snowstorm.ecl;

import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.ExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.validation.constraints.Null;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class ECLQueryService {

	@Autowired
	private ECLQueryBuilder queryBuilder;

	@Autowired
	private QueryService queryService;

	public Optional<Page<Long>> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, null, null);
	}

	public Optional<Page<Long>> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated, PageRequest pageRequest) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, null, pageRequest);
	}

	public Optional<Page<Long>> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated, Collection<Long> conceptIdFilter) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, conceptIdFilter, null);
	}

	public Optional<Page<Long>> selectConceptIds(String ecl, QueryBuilder branchCriteria, String path, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest) throws ECLException {
		ExpressionConstraint expressionConstraint = queryBuilder.createQuery(ecl);
		return expressionConstraint.select(path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService);
	}


}
