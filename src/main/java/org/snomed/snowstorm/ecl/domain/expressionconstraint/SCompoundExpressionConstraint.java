package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.kaicode.elasticvc.api.BranchCriteria;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstorm.ecl.ConceptSelectorHelper;
import org.snomed.snowstorm.ecl.ECLContentService;
import org.snomed.snowstorm.ecl.deserializer.ECLModelDeserializer;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.collect.Sets.union;
import static java.util.stream.Collectors.toSet;

public class SCompoundExpressionConstraint extends CompoundExpressionConstraint implements SExpressionConstraint {

	@Override
	public Optional<Page<Long>> select(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter,
			PageRequest pageRequest, ECLContentService eclContentService, boolean triedCache) {

		return Optional.of(ConceptSelectorHelper.select(this, branchCriteria, stated, conceptIdFilter, pageRequest, eclContentService, triedCache));
	}

	@Override
	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		return Optional.of(ConceptSelectorHelper.select(this, refinementBuilder));
	}

	@Override
	public Set<String> getConceptIds() {
		Set<String> conceptIds = newHashSet();
		if (conjunctionExpressionConstraints != null) {
			conceptIds.addAll(getConceptIds(conjunctionExpressionConstraints));
		} else if (disjunctionExpressionConstraints != null) {
			conceptIds.addAll(getConceptIds(disjunctionExpressionConstraints));
		} else {
			conceptIds.addAll(((SSubExpressionConstraint) exclusionExpressionConstraints.getFirst()).getConceptIds());
			conceptIds.addAll(((SSubExpressionConstraint) exclusionExpressionConstraints.getSecond()).getConceptIds());
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

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder, Consumer<List<Long>> filteredOrSupplementedContentCallback, boolean triedCache) {
		triedCache = false;// None of the compound constraints have been through caching

		if (conjunctionExpressionConstraints != null) {
			if (anyWithFiltersOrSupplements(conjunctionExpressionConstraints) || anyMemberOfQuery(conjunctionExpressionConstraints)) {
				// Prefetch all
				Set<Long> result = null;
				for (SubExpressionConstraint conjunctionExpressionConstraint : conjunctionExpressionConstraints) {
					List<Long> ids = ConceptSelectorHelper.select((SSubExpressionConstraint) conjunctionExpressionConstraint, refinementBuilder).getContent();
					Set<Long> resultSet = new LongLinkedOpenHashSet(ids);
					if (result == null) {
						result = resultSet;
					} else {
						result = union(result, resultSet);
					}
				}
				filteredOrSupplementedContentCallback.accept(result != null ? sortedList(result) : null);

			} else {
				for (SubExpressionConstraint conjunctionExpressionConstraint : conjunctionExpressionConstraints) {
					((SSubExpressionConstraint) conjunctionExpressionConstraint).addCriteria(refinementBuilder, (ids) -> {}, triedCache);
				}
			}
		} else if (disjunctionExpressionConstraints != null) {
			if (anyWithFiltersOrSupplements(disjunctionExpressionConstraints) || anyMemberOfQuery(disjunctionExpressionConstraints)) {
				// Prefetch all
				Set<Long> result = null;
				for (SubExpressionConstraint disjunctionExpressionConstraint : disjunctionExpressionConstraints) {
					List<Long> ids = ConceptSelectorHelper.select((SSubExpressionConstraint) disjunctionExpressionConstraint, refinementBuilder).getContent();
					if (result == null) {
						result = new LongOpenHashSet(ids);
					} else {
						result.addAll(ids);
					}
				}
				filteredOrSupplementedContentCallback.accept(result != null ? sortedList(result) : null);

			} else {
				BoolQuery.Builder queryBuilder = bool();
				for (SubExpressionConstraint disjunctionExpressionConstraint : disjunctionExpressionConstraints) {
					BoolQuery.Builder shouldQueryBuilder = bool();
					((SSubExpressionConstraint) disjunctionExpressionConstraint).addCriteria(new SubRefinementBuilder(refinementBuilder, shouldQueryBuilder), (ids) -> {}, triedCache);
					queryBuilder.should(shouldQueryBuilder.build()._toQuery());
				}
				refinementBuilder.getQueryBuilder().must(queryBuilder.build()._toQuery());

			}
		} else {
			SSubExpressionConstraint first = (SSubExpressionConstraint) exclusionExpressionConstraints.getFirst();
			SSubExpressionConstraint second = (SSubExpressionConstraint) exclusionExpressionConstraints.getSecond();

			if (first.isAnyFiltersOrSupplements() || second.isAnyFiltersOrSupplements() || anyMemberOfQuery(List.of(first,second))) {
				List<Long> ids = new LongArrayList(ConceptSelectorHelper.select(first, refinementBuilder).getContent());
				ids.removeAll(ConceptSelectorHelper.select(second, refinementBuilder).getContent());
				filteredOrSupplementedContentCallback.accept(ids);

			} else {
				first.addCriteria(refinementBuilder, (ids) -> {}, triedCache);
				BoolQuery.Builder mustNotQueryBuilder = bool();
				second.addCriteria(new SubRefinementBuilder(refinementBuilder, mustNotQueryBuilder), (ids) -> {}, triedCache);
				refinementBuilder.getQueryBuilder().mustNot(mustNotQueryBuilder.build()._toQuery());
			}
		}
	}

	private LongArrayList sortedList(Set<Long> result) {
		LongArrayList longs = new LongArrayList(result);
		longs.sort(null);
		return longs;
	}

	private boolean anyWithFiltersOrSupplements(List<SubExpressionConstraint> subExpressionConstraints) {
		return subExpressionConstraints.stream().anyMatch(constraint -> ((SSubExpressionConstraint) constraint).isAnyFiltersOrSupplements());
	}

	private boolean anyMemberOfQuery(List<SubExpressionConstraint> subExpressionConstraints) {
		return subExpressionConstraints.stream().anyMatch(constraint -> ((SSubExpressionConstraint) constraint).isMemberOfQuery()
				|| ((SSubExpressionConstraint) constraint).isNestedExpressionConstraintMemberOfQuery());
	}

	@Override
	public String toEclString() {
		return toString(new StringBuffer()).toString();
	}

	public StringBuffer toString(StringBuffer buffer) {
		boolean first = true;
		if (conjunctionExpressionConstraints != null) {
			for (SubExpressionConstraint expressionConstraint : conjunctionExpressionConstraints) {
				if (!first) {
					buffer.append(", ");
				}
				ECLModelDeserializer.expressionConstraintToString(expressionConstraint, buffer);
				first = false;
			}
		} else if (disjunctionExpressionConstraints != null) {
			for (SubExpressionConstraint expressionConstraint : disjunctionExpressionConstraints) {
				if (!first) {
					buffer.append(" or ");
				}
				ECLModelDeserializer.expressionConstraintToString(expressionConstraint, buffer);
				first = false;
			}
		} else {
			ECLModelDeserializer.expressionConstraintToString(exclusionExpressionConstraints.getFirst(), buffer);
			buffer.append(" minus ");
			ECLModelDeserializer.expressionConstraintToString(exclusionExpressionConstraints.getSecond(), buffer);
		}
		return buffer;
	}
}
