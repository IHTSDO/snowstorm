package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.stream.Collectors.toSet;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.springframework.util.CollectionUtils.isEmpty;

public class SCompoundExpressionConstraint extends CompoundExpressionConstraint implements SExpressionConstraint {

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
		if (!isEmpty(conjunctionExpressionConstraints)) {
			conceptIds.addAll(getConceptIds(conjunctionExpressionConstraints));
		}
		if (!isEmpty(disjunctionExpressionConstraints)) {
			conceptIds.addAll(getConceptIds(disjunctionExpressionConstraints));
		}
		if (exclusionExpressionConstraint != null) {
			conceptIds.addAll(((SSubExpressionConstraint) exclusionExpressionConstraint).getConceptIds());
		}
		return conceptIds;
	}

	private Set<String> getConceptIds(List<SubExpressionConstraint> subExpressionConstraints) {
		return subExpressionConstraints.stream()
				.map(SSubExpressionConstraint.class::cast)
				.map(SSubExpressionConstraint::getConceptIds)
				.flatMap(Set::stream)
				.collect(toSet());
	}

	public void addCriteria(RefinementBuilder refinementBuilder) {
		if (conjunctionExpressionConstraints != null) {
			for (SubExpressionConstraint conjunctionExpressionConstraint : conjunctionExpressionConstraints) {
				((SSubExpressionConstraint)conjunctionExpressionConstraint).addCriteria(refinementBuilder);
			}
		}
		if (disjunctionExpressionConstraints != null) {
			BoolQueryBuilder shouldQueries = boolQuery();
			refinementBuilder.getQuery().must(shouldQueries);
			for (SubExpressionConstraint disjunctionExpressionConstraint : disjunctionExpressionConstraints) {
				BoolQueryBuilder shouldQuery = boolQuery();
				shouldQueries.should(shouldQuery);
				((SSubExpressionConstraint)disjunctionExpressionConstraint).addCriteria(new SubRefinementBuilder(refinementBuilder, shouldQuery));
			}
		}
		if (exclusionExpressionConstraint != null) {
			BoolQueryBuilder mustNotQuery = boolQuery();
			refinementBuilder.getQuery().mustNot(mustNotQuery);
			((SSubExpressionConstraint)exclusionExpressionConstraint).addCriteria(new SubRefinementBuilder(refinementBuilder, mustNotQuery));
		}
	}
}
