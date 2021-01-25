package org.snomed.snowstorm.ecl.domain.refinement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.EclAttribute;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeGroup;
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

import static com.google.common.collect.Sets.newHashSet;
import static org.elasticsearch.index.query.QueryBuilders.*;

public class SEclAttribute extends EclAttribute implements SRefinement {

	private AttributeRange attributeRange;
	private RefinementBuilder refinementBuilder;

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		this.refinementBuilder = refinementBuilder;
		// Input validation
		if (cardinalityMin != null && cardinalityMax != null && cardinalityMin > cardinalityMax) {
			throw new IllegalArgumentException("Within cardinality constraints the minimum must not be greater than the maximum.");
		}

		BoolQueryBuilder query = refinementBuilder.getQuery();
		BranchCriteria branchCriteria = refinementBuilder.getBranchCriteria();
		boolean stated = refinementBuilder.isStated();

		if (reverse) {
			// Reverse flag for concept constraint query
			AttributeRange range = getAttributeRange();

			// Fetch the relationship destination concepts
			if (range.getPossibleAttributeValues() == null) {
				throw new UnsupportedOperationException("Returning the attribute values of all concepts is not supported.");
			}
			Collection<Long> destinationConceptIds = refinementBuilder.getQueryService()
					.findRelationshipDestinationIds(range.getPossibleAttributeValues().stream().map(Long::parseLong)
							.collect(Collectors.toList()), range.getAttributeTypeIds(), branchCriteria, stated);
			query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, destinationConceptIds));
		} else {
			// Not reverse flag
			CardinalityCriteria cardinalityCriteria = new CardinalityCriteria(cardinalityMin, cardinalityMax);
			if (cardinalityCriteria.noConstraintRequired) {
				return;
			}
			updateQueryWithCardinalityCriteria(query, cardinalityCriteria);
		}
	}

	@Override
	@JsonIgnore
	public EclAttributeGroup getParentGroup() {
		return super.getParentGroup();
	}

	private void updateQueryWithCardinalityCriteria(BoolQueryBuilder query, CardinalityCriteria cardinalityCriteria) {
		boolean mustOccur = cardinalityCriteria.mustOccur;
		boolean mustNotOccur = cardinalityCriteria.mustNotOccur;
		boolean specificCardinality = cardinalityCriteria.specificCardinality;

		if (specificCardinality) {
			refinementBuilder.inclusionFilterRequired();
		}
		boolean equalsOperator = isEqualOperator();

		AttributeRange range = getAttributeRange();
		List<String> possibleAttributeValues = range.getPossibleAttributeValues();
		Set<String> attributeTypeProperties = range.getPossibleAttributeTypes();
		if (possibleAttributeValues == null) {
			if (mustOccur || mustNotOccur) {
				// Value is wildcard
				BoolQueryBuilder oneOf = boolQuery();
				if (mustOccur == equalsOperator) {
					// Attribute just needs to exist
					query.must(oneOf);
				} else {
					// Attribute must not exist
					query.mustNot(oneOf);
				}
				for (String attributeTypeProperty : attributeTypeProperties) {
					oneOf.should(existsQuery(getAttributeTypeField(attributeTypeProperty)));
				}
			}
		} else {
			if (possibleAttributeValues.isEmpty()) {
				// Attribute value is not a wildcard but empty selection
				if (mustOccur) {
					// Force query to return nothing
					query.must(termQuery("force-nothing", "true"));
				}
			} else {
				// Value range established
				if (mustOccur) {
					if (isConcreteValueQuery()) {
						updateQueryWithConcreteValue(query, possibleAttributeValues, attributeTypeProperties);
					} else {
						if (equalsOperator) {
							// One of the attributes in the range must have a value in the range
							BoolQueryBuilder oneOf = boolQuery();
							query.must(oneOf);
							for (String attributeTypeProperty : attributeTypeProperties) {
								oneOf.should(termsQuery(getAttributeTypeField(attributeTypeProperty), possibleAttributeValues));
							}
						} else {
							BoolQueryBuilder oneOf = boolQuery();
							query.must(oneOf);
							// One of the attributes in the range must be present.
							// A concept may have the attribute value twice, one in and one outside the range.
							// This can not be expressed in this concept level query
							refinementBuilder.inclusionFilterRequired();
							for (String attributeTypeProperty : attributeTypeProperties) {
								oneOf.should(existsQuery(getAttributeTypeField(attributeTypeProperty)));
							}
						}
					}
				}
				if (mustNotOccur) {
					if (equalsOperator) {
						// None of the attributes in the range may have a value in the range
						for (String attributeTypeProperty : attributeTypeProperties) {
							query.mustNot(termsQuery(getAttributeTypeField(attributeTypeProperty), possibleAttributeValues));
						}
					} else {
						// None of the attributes in the range may have a value outside of the range
						// This can not be expressed in this concept level query
						refinementBuilder.inclusionFilterRequired();
					}
				}
			}
		}
	}

	private void updateQueryWithConcreteValue(BoolQueryBuilder query, List<String> possibleAttributeValues, Set<String> attributeTypeProperties) {
		// should just have one concrete value
		String value = possibleAttributeValues.get(0);
		if (isEqualOperator()) {
			// One of the attributes in the range must have a value in the range
			BoolQueryBuilder oneOf = boolQuery();
			query.must(oneOf);
			for (String attributeTypeProperty : attributeTypeProperties) {
				oneOf.should(termQuery(getAttributeTypeField(attributeTypeProperty), value));
			}
		} else {
			BoolQueryBuilder oneOf = boolQuery();
			query.must(oneOf);
			// concrete domain logic here
			String comparisonOperator = getAttributeRange().getOperator();
			for (String attributeTypeProperty : attributeTypeProperties) {
				if (getAttributeRange().isNumericQuery()) {
					if (">=".equals(comparisonOperator)) {
						oneOf.must(rangeQuery(getAttributeTypeField(attributeTypeProperty)).gte(value));
					} else if (">".equals(comparisonOperator)) {
						oneOf.must(rangeQuery(getAttributeTypeField(attributeTypeProperty)).gt(value));
					} else if ("<=".equals(comparisonOperator)) {
						oneOf.must(rangeQuery(getAttributeTypeField(attributeTypeProperty)).lte(value));
					} else if ("<".equals(comparisonOperator)) {
						oneOf.must(rangeQuery(getAttributeTypeField(attributeTypeProperty)).lt(value));
					} else if ("!=".equals(comparisonOperator)) {
						oneOf.must(existsQuery(getAttributeTypeField(attributeTypeProperty)));
						oneOf.mustNot(termQuery(getAttributeTypeField(attributeTypeProperty), value));
					}
				} else {
					if ("!=".equals(comparisonOperator)) {
						oneOf.must(existsQuery(getAttributeTypeField(attributeTypeProperty)));
						oneOf.mustNot(termQuery(getAttributeTypeField(attributeTypeProperty), value));
					}
				}
			}
		}
	}

	private boolean isEqualOperator() {
		return "=".equals(expressionComparisonOperator)
				|| "=".equals(getNumericComparisonOperator())
				|| "=".equals(getStringComparisonOperator());
	}

	@Override
	public Set<String> getConceptIds() {
		Set<String> conceptIds = newHashSet();
		conceptIds.addAll(((SSubExpressionConstraint) attributeName).getConceptIds());
		if (!isConcreteValueQuery()) {
			conceptIds.addAll(((SSubExpressionConstraint) value).getConceptIds());
		}
		return conceptIds;
	}

	private AttributeRange getAttributeRange() {
		if (attributeRange == null) {
			Optional<Page<Long>> attributeTypesOptional = ((SSubExpressionConstraint) attributeName).select(refinementBuilder);

			boolean attributeTypeWildcard = !attributeTypesOptional.isPresent();
			List<Long> attributeTypeIds;
			Set<String> attributeTypeProperties;
			if (attributeTypeWildcard) {
				attributeTypeIds = null;
				attributeTypeProperties = Collections.singleton(QueryConcept.ATTR_TYPE_WILDCARD);
			} else {
				attributeTypeIds = attributeTypesOptional.get().getContent();
				attributeTypeProperties = attributeTypeIds.stream().map(Object::toString).collect(Collectors.toSet());
				if (attributeTypeProperties.isEmpty()) {
					// Attribute type is not a wildcard but empty selection
					// Force query to return nothing
					attributeTypeProperties.add(SExpressionConstraintHelper.MISSING);
				}
			}

			if (!isConcreteValueQuery()) {
				List<Long> possibleAttributeValuesLong = ((SSubExpressionConstraint) value).select(refinementBuilder).map(Slice::getContent).orElse(null);
				List<String> possibleAttributeValues = possibleAttributeValuesLong != null ? possibleAttributeValuesLong.stream().map(String::valueOf).collect(Collectors.toList()) : null;
				attributeRange = AttributeRange.newConceptRange(attributeTypeWildcard, attributeTypeIds, attributeTypeProperties, getExpressionComparisonOperator(),
						possibleAttributeValues, cardinalityMin, cardinalityMax);
			} else {
				if (getNumericComparisonOperator() != null) {
					if (attributeTypeWildcard) {
						attributeTypeProperties = Collections.singleton(QueryConcept.ATTR_NUMERIC_TYPE_WILDCARD);
					}
					attributeRange = AttributeRange.newConcreteNumberRange(attributeTypeWildcard, attributeTypeIds, attributeTypeProperties, getNumericComparisonOperator(),
							getNumericValue(), cardinalityMin, cardinalityMax);

				} else if (getStringComparisonOperator() != null) {
					attributeRange = AttributeRange.newConcreteStringRange(attributeTypeWildcard, attributeTypeIds, attributeTypeProperties, getStringComparisonOperator(),
							getStringValue(), cardinalityMin, cardinalityMax);
				}
			}
		}
		return attributeRange;
	}

	void checkConceptConstraints(MatchContext matchContext) {
		AttributeRange range = getAttributeRange();
		Map<Integer, Map<String, List<Object>>> conceptAttributes = matchContext.getConceptAttributes();
		boolean withinGroup = matchContext.isWithinGroup();
		// Count occurrence of this attribute within each group
		final AtomicInteger attributeMatchCount = new AtomicInteger(0);
		final Map<Integer, AtomicInteger> groupAttributeMatchCounts = new HashMap<>();

		for (Map.Entry<Integer, Map<String, List<Object>>> attributeGroup : conceptAttributes.entrySet()) {
			attributeGroup.getValue().entrySet().stream()
					.filter(entrySet -> range.isTypeWithinRange(entrySet.getKey()))
					.forEach((Map.Entry<String, List<Object>> entrySet) ->
							entrySet.getValue().forEach(conceptAttributeValue -> {
								if (range.isValueWithinRange(conceptAttributeValue)) {
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
				if ((range.getCardinalityMin() == null || range.getCardinalityMin() <= inGroupAttributeMatchCount.get())
						&& (range.getCardinalityMax() == null || range.getCardinalityMax() >= inGroupAttributeMatchCount.get())) {
					matchingGroups.add(group);
				}
			}
		} else {
			// Apply attribute cardinality across whole concept
			if ((range.getCardinalityMin() == null || range.getCardinalityMin() <= attributeMatchCount.get())
					&& (range.getCardinalityMax() == null || range.getCardinalityMax() >= attributeMatchCount.get())) {

				matchingGroups.add(-1);
			}
		}

		matchContext.setMatchingGroups(matchingGroups);
	}

	private String getAttributeTypeField(String attributeTypeProperty) {
		return QueryConcept.Fields.ATTR + "." + attributeTypeProperty;
	}

	private boolean isConcreteValueQuery() {
		return getNumericComparisonOperator() != null || getStringComparisonOperator() != null;
	}

	private static class CardinalityCriteria {
		// The query is not capable of constraining the number of times an attribute occurs, only that it does or does not occur.
		// Further cardinality checking against fetched index records can be enabled using the specificCardinality flag.
		private boolean specificCardinality = false;
		private boolean mustOccur = false;
		private boolean mustNotOccur = false;
		private boolean noConstraintRequired = false;

		CardinalityCriteria(Integer cardinalityMin, Integer cardinalityMax) {
			// Cardinality scenarios:
			// Unbound
			// *..* / 0..*
			if ((cardinalityMin == null && cardinalityMax == null) ||
					(isZero(cardinalityMin) && cardinalityMax == null)) {
				// no constraints needed
				noConstraintRequired = true;
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
				mustNotOccur = true;
			}

			// One or more
			else if (cardinalityMin != null && cardinalityMin == 1 && cardinalityMax == null) {
				// must occur
				mustOccur = true;
				// any number of times
			}

			// Specific positive cardinality
			else {
				// must occur
				mustOccur = true;
				// certain number of times
				specificCardinality = true;
			}
		}

		private boolean isZero(Integer i) {
			return i != null && i == 0;
		}

		private boolean isOneOrMore(Integer i) {
			return i != null && i > 0;
		}
	}
}
