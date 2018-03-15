package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.RefinementBuilderImpl;
import org.snomed.snowstorm.ecl.domain.refinement.Refinement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.Optional;

public abstract class ExpressionConstraint implements Refinement {

	// Used to force no match
	public static final String MISSING = "missing";
	public static final Long MISSING_LONG = 111L;

	public Optional<Page<Long>> select(String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		if (isWildcard()) {
			return Optional.empty();
		}
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);
		RefinementBuilder refinementBuilder = new RefinementBuilderImpl(query, path, branchCriteria, stated, queryService);
		addCriteria(refinementBuilder);
		return Optional.of(ConceptSelectorHelper.fetchIds(query, conceptIdFilter, pageRequest, queryService));
	}

	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		return select(refinementBuilder.getPath(), refinementBuilder.getBranchCriteria(), refinementBuilder.isStated(), null, null, refinementBuilder.getQueryService());
	}

	protected abstract boolean isWildcard();
}
