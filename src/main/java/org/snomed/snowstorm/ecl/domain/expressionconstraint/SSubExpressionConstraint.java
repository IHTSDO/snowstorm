package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import io.kaicode.elasticvc.api.BranchCriteria;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

public class SSubExpressionConstraint extends SubExpressionConstraint implements SExpressionConstraint {

	public SSubExpressionConstraint(Operator operator) {
		super(operator);
	}

	@Override
	public Optional<Page<Long>> select(String path, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		if (isUnconstrained()) {
			return Optional.empty();
		}
		return SExpressionConstraintHelper.select(this, path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService);
	}

	@Override
	public Optional<Page<Long>> select(String path, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService, String batchMode) {
		if (isUnconstrained()) {
			return Optional.empty();
		}
		return SExpressionConstraintHelper.select(this, path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService, batchMode);
	}

	private boolean isUnconstrained() {
		return wildcard && (operator == null || operator == Operator.descendantorselfof || operator == Operator.ancestororselfof);
	}

	@Override
	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		if (isUnconstrained()) {
			return Optional.empty();
		}
		return SExpressionConstraintHelper.select(this, refinementBuilder);
	}

	@Override
	public void setNestedExpressionConstraint(ExpressionConstraint nestedExpressionConstraint) {
		if (operator == Operator.memberOf) {
			throw new UnsupportedOperationException("MemberOf nested expression constraint is not supported.");
		}
		super.setNestedExpressionConstraint(nestedExpressionConstraint);
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		BoolQueryBuilder query = refinementBuilder.getQuery();
		if (conceptId != null) {
			if (operator != null) {
				applyConceptCriteriaWithOperator(Collections.singleton(parseLong(conceptId)), operator, refinementBuilder);
			} else {
				query.must(QueryBuilders.termQuery(QueryConcept.Fields.CONCEPT_ID, conceptId));
			}
		} else if (nestedExpressionConstraint != null) {
			Optional<Page<Long>> conceptIdsOptional = ((SExpressionConstraint)nestedExpressionConstraint).select(refinementBuilder);
			if (!conceptIdsOptional.isPresent()) {
				return;
			}
			List<Long> conceptIds = conceptIdsOptional.get().getContent();
			if (conceptIds.isEmpty()) {
				// Attribute type is not a wildcard but empty selection
				// Force query to return nothing
				conceptIds = Collections.singletonList(SExpressionConstraintHelper.MISSING_LONG);
			}
			BoolQueryBuilder filterQuery = boolQuery();
			query.filter(filterQuery);
			if (operator != null) {
				SubRefinementBuilder filterRefinementBuilder = new SubRefinementBuilder(refinementBuilder, filterQuery);
				applyConceptCriteriaWithOperator(conceptIds, operator, filterRefinementBuilder);
			} else {
				filterQuery.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds));
			}
		} else if (operator == Operator.memberOf) {
			// Member of wildcard (any reference set)
			query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, refinementBuilder.getQueryService().findConceptIdsInReferenceSet(refinementBuilder.getBranchCriteria(), null)));
		} else if (operator == Operator.descendantof || operator == Operator.childof) {
			// Descendant of wildcard / Child of wildcard = anything but root
			query.mustNot(termQuery(QueryConcept.Fields.CONCEPT_ID, Concepts.SNOMEDCT_ROOT));
		} else if (operator == Operator.ancestorof || operator == Operator.parentof) {
			// Ancestor of wildcard / Parent of wildcard = all non-leaf concepts
			Collection<Long> conceptsWithDescendants = refinementBuilder.getQueryService().findRelationshipDestinationIds(
					null, Collections.singletonList(parseLong(Concepts.ISA)), refinementBuilder.getBranchCriteria(), refinementBuilder.isStated());
			query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptsWithDescendants));
		}
		// Else Wildcard! which has no constraints
	}

	private void applyConceptCriteriaWithOperator(Collection<Long> conceptIds, Operator operator, RefinementBuilder refinementBuilder) {
		BoolQueryBuilder query = refinementBuilder.getQuery();
		QueryService queryService = refinementBuilder.getQueryService();
		BranchCriteria branchCriteria = refinementBuilder.getBranchCriteria();
		String path = refinementBuilder.getPath();
		boolean stated = refinementBuilder.isStated();

		switch (operator) {
			case childof:
				query.must(termsQuery(QueryConcept.Fields.PARENTS, conceptIds));
				break;
			case descendantorselfof:
				// <<
				query.must(
						boolQuery()
								.should(termsQuery(QueryConcept.Fields.ANCESTORS, conceptIds))
								.should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds))
				);
				break;
			case descendantof:
				// <
				query.must(termsQuery(QueryConcept.Fields.ANCESTORS, conceptIds));
				break;
			case parentof:
				for (Long conceptId : conceptIds) {
					Set<Long> parents = queryService.findParentIds(branchCriteria, stated, conceptId.toString());
					query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, parents));
				}
				break;
			case ancestororselfof:
				Set<Long> allAncestors = retrieveAllAncestors(conceptIds, branchCriteria, path, stated, queryService);
				query.must(
						boolQuery()
								.should(termsQuery(QueryConcept.Fields.CONCEPT_ID, allAncestors))
								.should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds))
				);
				break;
			case ancestorof:
				// > x
				Set<Long> allAncestors2 = retrieveAllAncestors(conceptIds, branchCriteria, path, stated, queryService);
				query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, allAncestors2));
				break;
			case memberOf:
				// ^
				query.filter(termsQuery(QueryConcept.Fields.CONCEPT_ID, queryService.findConceptIdsInReferenceSet(branchCriteria, conceptId)));
				break;
		}
	}

	private Set<Long> retrieveAllAncestors(Collection<Long> conceptIds, BranchCriteria branchCriteria, String path, boolean stated, QueryService queryService) {
		Set<Long> allAncestors = new LongArraySet();
		for (Long conceptId : conceptIds) {
			allAncestors.addAll(queryService.findAncestorIds(branchCriteria, path, stated, conceptId.toString()));
		}
		return allAncestors;
	}

}
