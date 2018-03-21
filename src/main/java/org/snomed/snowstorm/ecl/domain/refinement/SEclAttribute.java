package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.EclAttribute;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SRefinement;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.MatchContext;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraintHelper;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class SEclAttribute extends EclAttribute implements SRefinement {

	private AttributeRange attributeRange;
	private RefinementBuilder refinementBuilder;

	@Override
	public void setNumericComparisonOperator(String numericComparisonOperator) {
		throw new UnsupportedOperationException("Only the ExpressionComparisonOperator is supported. NumericComparisonOperator and StringComparisonOperator are not supported.");
	}

	@Override
	public void setStringComparisonOperator(String stringComparisonOperator) {
		throw new UnsupportedOperationException("Only the ExpressionComparisonOperator is supported. NumericComparisonOperator and StringComparisonOperator are not supported.");
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		this.refinementBuilder = refinementBuilder;
		// Input validation
		if (cardinalityMin != null && cardinalityMax != null && cardinalityMin > cardinalityMax) {
			throw new IllegalArgumentException("Within cardinality constraints the minimum must not be greater than the maximum.");
		}
		if (isUnconstrained()) {
			// [0..*] means this constraint is meaningless
			return;
		}

		AttributeRange attributeRange = getAttributeRange();

		List<Long> possibleAttributeValues = attributeRange.getPossibleAttributeValues();
		Set<String> attributeTypeProperties = attributeRange.getPossibleAttributeTypes();

		BoolQueryBuilder query = refinementBuilder.getQuery();
		QueryBuilder branchCriteria = refinementBuilder.getBranchCriteria();
		boolean stated = refinementBuilder.isStated();

		if (reverse) {
			// Reverse flag

			// Fetch the relationship destination concepts
			if (possibleAttributeValues == null) {
				throw new UnsupportedOperationException("Returning the attribute values of all concepts is not supported.");
			}
			Collection<Long> destinationConceptIds = refinementBuilder.getQueryService()
					.retrieveRelationshipDestinations(possibleAttributeValues, attributeRange.getAttributeTypesOptional().map(Slice::getContent).orElse(null), branchCriteria, stated);
			query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, destinationConceptIds));

		} else {
			// Not reverse flag

			boolean cardinalityConstraints = cardinalityMin != null || cardinalityMax != null;

			if (cardinalityConstraints) {
				refinementBuilder.setInclusionFilterRequired(true);
			}

			if (expressionComparisonOperator.equals("=")) {

				boolean minCardinalityIsZero = cardinalityMin != null && cardinalityMin == 0;
				// If min cardinality is zero this attribute is not required so there is a danger of selecting everything
				// TODO - can we provide an optimisation for min cardinality zero where we only collect non matching concepts?

				boolean maxCardinalityIsZero = cardinalityMax != null && cardinalityMax == 0;
				// If max cardinality is zero this attribute must not be present

				if (!minCardinalityIsZero) {
					// Simple - just add attribute type and value criteria to main query builder
					doAddTypeAndValueCriteria(query, attributeTypeProperties, possibleAttributeValues, maxCardinalityIsZero);
				}

			} else {
				if (cardinalityConstraints) {
					// TODO Support attribute cardinality in combination with 'not equals'
					throw new UnsupportedOperationException("Attribute cardinality in combination with 'not equals' operator is not currently supported.");
				}
				for (String attributeTypeProperty : attributeTypeProperties) {
					if (possibleAttributeValues == null) {
						query.mustNot(existsQuery(getAttributeTypeField(attributeTypeProperty)));
					} else {
						query.must(existsQuery(getAttributeTypeField(attributeTypeProperty)));
						query.filter(boolQuery().mustNot(termsQuery(getAttributeTypeField(attributeTypeProperty), possibleAttributeValues)));
					}
				}
			}
		}
	}

	private AttributeRange getAttributeRange() {
		if (attributeRange == null) {
			Optional<Page<Long>> attributeTypesOptional = ((SSubExpressionConstraint) attributeName).select(refinementBuilder);

			boolean attributeTypeWildcard = !attributeTypesOptional.isPresent();
			Set<String> attributeTypeProperties_;
			if (attributeTypeWildcard) {
				attributeTypeProperties_ = Collections.singleton(QueryConcept.ATTR_TYPE_WILDCARD);
			} else {
				attributeTypeProperties_ = attributeTypesOptional.get().stream().map(Object::toString).collect(Collectors.toSet());
				if (attributeTypeProperties_.isEmpty()) {
					// Attribute type is not a wildcard but empty selection
					// Force query to return nothing
					attributeTypeProperties_.add(SExpressionConstraintHelper.MISSING);
				}
			}

			List<Long> possibleAttributeValues_ = ((SSubExpressionConstraint) value).select(refinementBuilder).map(Slice::getContent).orElse(null);

			attributeRange = new AttributeRange(attributeTypeWildcard, attributeTypesOptional, attributeTypeProperties_, possibleAttributeValues_, cardinalityMin, cardinalityMax);
		}
		return attributeRange;
	}

	private boolean isUnconstrained() {
		return cardinalityMin != null && cardinalityMin == 0 && cardinalityMax == null;
	}

	private void doAddTypeAndValueCriteria(BoolQueryBuilder query, Set<String> attributeTypeProperties, Collection<Long> possibleAttributeValues,
										   boolean maxCardinalityIsZero) {
		if (possibleAttributeValues == null) {
			// Value is wildcard, attribute just needs to exist
			BoolQueryBuilder oneOf = boolQuery();
			if (!maxCardinalityIsZero) {
				query.must(oneOf);
			} else {
				query.mustNot(oneOf);
			}
			for (String attributeTypeProperty : attributeTypeProperties) {
				oneOf.should(existsQuery(getAttributeTypeField(attributeTypeProperty)));
			}
		} else {
			if (possibleAttributeValues.isEmpty()) {
				if (!maxCardinalityIsZero) {
					// Attribute value is not a wildcard but empty selection
					// Force query to return nothing
					query.must(termQuery("force-nothing", "true"));
				}
			} else {
				BoolQueryBuilder oneOf = boolQuery();
				if (!maxCardinalityIsZero) {
					query.must(oneOf);
				} else {
					query.mustNot(oneOf);
				}
				for (String attributeTypeProperty : attributeTypeProperties) {
					oneOf.should(termsQuery(getAttributeTypeField(attributeTypeProperty), possibleAttributeValues));
				}
			}
		}
	}

	void checkConceptConstraints(MatchContext matchContext) {
		attributeRange = getAttributeRange();
		Map<Integer, Map<String, List<String>>> conceptAttributes = matchContext.getConceptAttributes();
		boolean withinGroup = matchContext.isWithinGroup();

		// Count occurrence of this attribute within each group
		final AtomicInteger attributeMatchCount = new AtomicInteger(0);
		final Map<Integer, AtomicInteger> groupAttributeMatchCounts = new HashMap<>();

		for (Map.Entry<Integer, Map<String, List<String>>> attributeGroup : conceptAttributes.entrySet()) {
			attributeGroup.getValue().entrySet().stream()
					.filter(entrySet -> attributeRange.isTypeAcceptable(entrySet.getKey()))
					.forEach((Map.Entry<String, List<String>> entrySet) ->
							entrySet.getValue().forEach(conceptAttributeValue -> {
								if (attributeRange.isValueAcceptable(conceptAttributeValue)) {
									// Increment count for attribute match in the concept
									attributeMatchCount.incrementAndGet();
									// Increment count for attribute match in this relationship group
									groupAttributeMatchCounts.computeIfAbsent(attributeGroup.getKey(), i -> new AtomicInteger()).incrementAndGet();
								}
							})
					);
		}

		// Gather the group number of groups with this attribute
		Set<Integer> matchingGroups = new HashSet<>();

		if (withinGroup) {
			// Apply attribute cardinality within each group
			for (Integer group : groupAttributeMatchCounts.keySet()) {
				if (group == 0) {
					continue; // Group 0 is not a group
					// TODO: Should we let MRCM self-grouped attributes through here?
				}
				AtomicInteger inGroupAttributeMatchCount = groupAttributeMatchCounts.get(group);
				if ((attributeRange.getCardinalityMin() == null || attributeRange.getCardinalityMin() <= inGroupAttributeMatchCount.get())
						&& (attributeRange.getCardinalityMax() == null || attributeRange.getCardinalityMax() >= inGroupAttributeMatchCount.get())) {
					matchingGroups.add(group);
				}
			}
		} else {
			// Apply attribute cardinality across whole concept
			if ((attributeRange.getCardinalityMin() == null || attributeRange.getCardinalityMin() <= attributeMatchCount.get())
					&& (attributeRange.getCardinalityMax() == null || attributeRange.getCardinalityMax() >= attributeMatchCount.get())) {

				matchingGroups.add(-1);
			}
		}

		matchContext.setMatchingGroups(matchingGroups);
	}

	private String getAttributeTypeField(String attributeTypeProperty) {
		return QueryConcept.ATTR_FIELD + "." + attributeTypeProperty;
	}

}
