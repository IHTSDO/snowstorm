package org.snomed.snowstorm.ecl.domain.refinement;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
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

import static com.google.common.collect.Sets.newHashSet;
import static org.elasticsearch.index.query.QueryBuilders.*;

public class SEclAttribute extends EclAttribute implements SRefinement {

	private AttributeRange attributeRange;
	private RefinementBuilder refinementBuilder;

	@Override
	public void setNumericComparisonOperator(String numericComparisonOperator) {
		super.setNumericComparisonOperator(numericComparisonOperator);
	}

	@Override
	public void setStringComparisonOperator(String stringComparisonOperator) {
		super.setStringComparisonOperator(stringComparisonOperator);
	}

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
			if (!isConcreteValueQuery()) {
				// Reverse flag for concept constraint query
				AttributeRange attributeRange = getAttributeRange();

				// Fetch the relationship destination concepts
				if (attributeRange.getPossibleAttributeValues() == null) {
					throw new UnsupportedOperationException("Returning the attribute values of all concepts is not supported.");
				}
				Collection<Long> destinationConceptIds = refinementBuilder.getQueryService()
						.findRelationshipDestinationIds(attributeRange.getPossibleAttributeValues().stream().map(Long::parseLong).collect(Collectors.toList()), attributeRange.getAttributeTypesOptional().map(Slice::getContent).orElse(null), branchCriteria, stated);
				query.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, destinationConceptIds));
			}
		} else {
			// Not reverse flag

			// The query is not capable of constraining the number of times an attribute occurs, only that it does or does not occur.
			// Further cardinality checking against fetched index records can be enabled using the specificCardinality flag.
			boolean specificCardinality = false;
			boolean mustOccur = false;
			boolean mustNotOccur = false;

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

			if (specificCardinality) {
				refinementBuilder.inclusionFilterRequired();
			}


			boolean equalsOperator = isEqualOperator();

			AttributeRange attributeRange = getAttributeRange();
			List<String> possibleAttributeValues = attributeRange.getPossibleAttributeValues();
			Set<String> attributeTypeProperties = attributeRange.getPossibleAttributeTypes();
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
						if (equalsOperator) {
							// One of the attributes in the range must have a value in the range
							BoolQueryBuilder oneOf = boolQuery();
							query.must(oneOf);
							for (String attributeTypeProperty : attributeTypeProperties) {
								oneOf.should(termsQuery(getAttributeTypeField(attributeTypeProperty), possibleAttributeValues));
							}
						} else {
							// One of the attributes in the range must be present.
							// A concept may have the attribute value twice, one in and one outside the range.
							// This can not be expressed in this concept level query
							refinementBuilder.inclusionFilterRequired();

							BoolQueryBuilder oneOf = boolQuery();
							query.must(oneOf);
							for (String attributeTypeProperty : attributeTypeProperties) {
								oneOf.should(existsQuery(getAttributeTypeField(attributeTypeProperty)));
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

			if (!isConcreteValueQuery()) {
				List<Long> possibleAttributeValues_ = ((SSubExpressionConstraint) value).select(refinementBuilder).map(Slice::getContent).orElse(null);
				List<String> possibleAttributeValues = possibleAttributeValues_ != null ? possibleAttributeValues_.stream().map(String::valueOf).collect(Collectors.toList()) : null;
				attributeRange = new AttributeRange(attributeTypeWildcard, attributeTypesOptional, attributeTypeProperties_, possibleAttributeValues, cardinalityMin, cardinalityMax);
			} else {
				List<String> concreteValues = getConcreteValues();
				attributeRange = new AttributeRange(attributeTypeWildcard, attributeTypesOptional, attributeTypeProperties_, concreteValues, cardinalityMin, cardinalityMax);
				String comparator = null;
				boolean isNumeric = false;
				if (getNumericComparisonOperator() != null) {
					comparator = getNumericComparisonOperator();
					isNumeric = true;
				} else if (getStringComparisonOperator() != null) {
					comparator = getStringComparisonOperator();
				}
				attributeRange.setConcreteValueComparator(comparator);
				attributeRange.setIsNumeric(isNumeric);
			}
		}
		return attributeRange;
	}

	private List<String> getConcreteValues() {
		if (getStringComparisonOperator() != null && getStringValue() != null) {
			// TODO remove this debug
			System.out.println("string value = " + getStringValue());
			return Arrays.asList(getStringValue());
		}
		if (getNumericComparisonOperator() != null && getNumericValue() != null) {
			return Arrays.asList(getNumericValue());
		}
		return null;
	}

	void checkConceptConstraints(MatchContext matchContext) {
		attributeRange = getAttributeRange();
		Map<Integer, Map<String, List<String>>> conceptAttributes = matchContext.getConceptAttributes();
		boolean withinGroup = matchContext.isWithinGroup();
		boolean equalsOperator = isConcreteValueQuery() ? true : isEqualOperator();

		// Count occurrence of this attribute within each group
		final AtomicInteger attributeMatchCount = new AtomicInteger(0);
		final Map<Integer, AtomicInteger> groupAttributeMatchCounts = new HashMap<>();

		for (Map.Entry<Integer, Map<String, List<String>>> attributeGroup : conceptAttributes.entrySet()) {
			attributeGroup.getValue().entrySet().stream()
					.filter(entrySet -> attributeRange.isTypeWithinRange(entrySet.getKey()))
					.forEach((Map.Entry<String, List<String>> entrySet) ->
							entrySet.getValue().forEach(conceptAttributeValue -> {
								if (equalsOperator == attributeRange.isValueWithinRange(conceptAttributeValue)) {
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
		return QueryConcept.Fields.ATTR + "." + attributeTypeProperty;
	}

	private boolean isConcreteValueQuery() {
		return getNumericComparisonOperator() != null || getStringComparisonOperator() != null;
	}
}
