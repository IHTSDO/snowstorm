package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.refinement.SEclRefinement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static com.google.common.collect.Sets.newHashSet;

public class SRefinedExpressionConstraint extends RefinedExpressionConstraint implements SExpressionConstraint {

	public SRefinedExpressionConstraint(SubExpressionConstraint subExpressionConstraint, EclRefinement eclRefinement) {
		super(subExpressionConstraint, eclRefinement);
	}

	@Override
	public Optional<Page<Long>> select(String path, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		return SExpressionConstraintHelper.select(this, path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService);
	}

	@Override
	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		return SExpressionConstraintHelper.select(this, refinementBuilder);
	}

	@Override
	public Set<String> getConceptIds() {
		Set<String> conceptIds = newHashSet();
		if (subexpressionConstraint != null) {
			conceptIds.addAll(((SSubExpressionConstraint) subexpressionConstraint).getConceptIds());
		}
		if (eclRefinement != null) {
			conceptIds.addAll(((SEclRefinement) eclRefinement).getConceptIds());
		}
		return conceptIds;
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		((SSubExpressionConstraint)subexpressionConstraint).addCriteria(refinementBuilder);
		((SEclRefinement)eclRefinement).addCriteria(refinementBuilder);

		if (refinementBuilder.isInclusionFilterRequired()) {
			refinementBuilder.setInclusionFilter(queryConcept -> {
				Map<Integer, Map<String, List<String>>> conceptAttributes = queryConcept.getGroupedAttributesMap();
				MatchContext matchContext = new MatchContext(conceptAttributes);
				return ((SEclRefinement) eclRefinement).isMatch(matchContext);
			});
		}
	}

}
