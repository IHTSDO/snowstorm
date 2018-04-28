package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.ConceptSelectorHelper;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.RefinementBuilderImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.Optional;

public class SExpressionConstraintHelper {

	// Used to force no match
	public static final String MISSING = "missing";
	public static final Long MISSING_LONG = 111L;

	protected static Optional<Page<Long>> select(SExpressionConstraint sExpressionConstraint, String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);
		RefinementBuilder refinementBuilder = new RefinementBuilderImpl(query, path, branchCriteria, stated, queryService);
		sExpressionConstraint.addCriteria(refinementBuilder);
		return Optional.of(ConceptSelectorHelper.fetchIds(query, conceptIdFilter, refinementBuilder.getInclusionFilter(), pageRequest, queryService));
	}

	protected static Optional<Page<Long>> select(SExpressionConstraint sExpressionConstraint, RefinementBuilder refinementBuilder) {
		return select(sExpressionConstraint, refinementBuilder.getPath(), refinementBuilder.getBranchCriteria(), refinementBuilder.isStated(), null, null, refinementBuilder.getQueryService());
	}

}
