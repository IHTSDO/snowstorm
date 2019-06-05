package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
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

	protected static Optional<Page<Long>> select(SExpressionConstraint sExpressionConstraint, String path, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService, String batchMode) {
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria.getEntityBranchCriteria(QueryConcept.class), stated);
		RefinementBuilder refinementBuilder = new RefinementBuilderImpl(query, path, branchCriteria, stated, queryService);
		sExpressionConstraint.addCriteria(refinementBuilder);
		return Optional.of(ConceptSelectorHelper.fetchIds(query, conceptIdFilter, refinementBuilder.getInclusionFilter(), pageRequest, queryService));
	}

	protected static Optional<Page<Long>> select(SExpressionConstraint sExpressionConstraint, String path, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		return select(sExpressionConstraint, path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService, "N");
	}

	protected static Optional<Page<Long>> select(SExpressionConstraint sExpressionConstraint, RefinementBuilder refinementBuilder) {
		return select(sExpressionConstraint, refinementBuilder.getPath(), refinementBuilder.getBranchCriteria(), refinementBuilder.isStated(), null, null, refinementBuilder.getQueryService());
	}

}
