package org.snomed.snowstorm.ecl.domain.refinement;

import io.swagger.models.auth.In;
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

		BoolQueryBuilder query = refinementBuilder.getQuery();
		QueryBuilder branchCriteria = refinementBuilder.getBranchCriteria();
		boolean stated = refinementBuilder.isStated();

		if (reverse) {
			// Reverse flag

			AttributeRange attributeRange = getAttributeRange();

			// Fetch the relationship destination concepts
			if (attributeRange.getPossibleAttributeValues() == null) {
				throw new UnsupportedOperationException("Returning the attribute values of all concepts is not supported.");
			}
			Collection<Long> destinationConceptIds = refinementBuilder.getQueryService()
					.retrieveRelationshipDestinations(attributeRange.getPossibleAttributeValues(), attributeRange.getAttributeTypesOptional().map(Slice::getContent).orElse(null), branchCriteria, stated);
			query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, destinationConceptIds));

		} else {
			// Not reverse flag

			// The query is not capable of constraining the number of times an attribute occurs, only that it does or does not occur.
			// Further cardinality checking against fetched index records can be enabled using the specificCardinality flag.
			boolean specificCardinality = false;
			BoolQueryBuilder withinCardinality = boolQuery();

			// Cardinality scenarios:

			// Unbound
			// *..* / 0..*
			if ((cardinalityMin == null && cardinalityMax == null) ||
					(isZero(cardinalityMin) && cardinalityMax == null)) {
				// no constraints needed
				return;
			}

			// Optional but bounded
			// *..+N / 0..+N
			else if ((cardinalityMin == null && isOneOrMore(cardinalityMax)) ||
					isZero(cardinalityMin) && isOneOrMore(cardinalityMax)) {
				specificCardinality = true;
			}

			// Max zero
			// x..0
			else if (isZero(cardinalityMax)) {
				// must NOT occur
				query.mustNot(withinCardinality);
			}

			// One or more
			else if (cardinalityMin != null && cardinalityMin == 1 && cardinalityMax == null) {
				// must occur
				query.must(withinCardinality);
				// any number of times
			}

			// Specific positive cardinality
			else {
				// must occur
				query.must(withinCardinality);
				// certain number of times
				specificCardinality = true;
			}

			if (specificCardinality) {
				refinementBuilder.setInclusionFilterRequired(true);
			}


			boolean equalsOperator = expressionComparisonOperator.equals("=");

			AttributeRange attributeRange = getAttributeRange();
			List<Long> possibleAttributeValues = attributeRange.getPossibleAttributeValues();
			Set<String> attributeTypeProperties = attributeRange.getPossibleAttributeTypes();
			if (possibleAttributeValues == null) {
				// Value is wildcard
				BoolQueryBuilder oneOf = boolQuery();
				if (equalsOperator) {
					// Attribute just needs to exist
					withinCardinality.must(oneOf);
				} else {
					// Attribute must not exist
					withinCardinality.mustNot(oneOf);
				}
				for (String attributeTypeProperty : attributeTypeProperties) {
					oneOf.should(existsQuery(getAttributeTypeField(attributeTypeProperty)));
				}
			} else {
				if (possibleAttributeValues.isEmpty()) {
					// Attribute value is not a wildcard but empty selection
					// Force query to return nothing
					withinCardinality.must(termQuery("force-nothing", "true"));
				} else {
					if (equalsOperator) {
						BoolQueryBuilder oneOf = boolQuery();
						withinCardinality.must(oneOf);
						for (String attributeTypeProperty : attributeTypeProperties) {
							oneOf.should(termsQuery(getAttributeTypeField(attributeTypeProperty), possibleAttributeValues));
						}
					} else {
						BoolQueryBuilder oneOf = boolQuery();
						withinCardinality.must(oneOf);
						for (String attributeTypeProperty : attributeTypeProperties) {
							oneOf.should(existsQuery(getAttributeTypeField(attributeTypeProperty)));
							withinCardinality.mustNot(termsQuery(getAttributeTypeField(attributeTypeProperty), possibleAttributeValues));
						}
					}
				}
			}
		}
	}

	private boolean isZero(Integer i) {
		return i != null && i == 0;
	}

	private boolean isOneOrMore(Integer i) {
		return i != null && i > 0;
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
