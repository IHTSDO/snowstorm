package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.ConceptSelectorHelper;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

public class EclAttribute implements Refinement {

	private SubExpressionConstraint attributeName;
	private String expressionComparisonOperator;
	private SubExpressionConstraint value;
	private boolean reverse;
	private EclAttributeGroup parentGroup;
	private Integer cardinalityMin;
	private Integer cardinalityMax;

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		// Input validation
		if (cardinalityMin != null && cardinalityMax != null && cardinalityMin > cardinalityMax) {
			throw new IllegalArgumentException("Within cardinality constraints the minimum must not be greater than the maximum.");
		}
		if (cardinalityMin != null && cardinalityMin == 0 && cardinalityMax == null) {
			// [0..*] means this constraint is meaningless
			return;
		}

		Optional<Page<Long>> attributeTypesOptional = attributeName.select(refinementBuilder);

		boolean attributeTypeWildcard = !attributeTypesOptional.isPresent();
		Set<String> attributeTypeProperties;
		if (attributeTypeWildcard) {
			attributeTypeProperties = Collections.singleton(QueryConcept.ATTR_TYPE_WILDCARD);
		} else {
			attributeTypeProperties = attributeTypesOptional.get().stream().map(Object::toString).collect(Collectors.toSet());
			if (attributeTypeProperties.isEmpty()) {
				// Attribute type is not a wildcard but empty selection
				// Force query to return nothing
				attributeTypeProperties.add(ExpressionConstraint.MISSING);
			}
		}

		List<Long> possibleAttributeValues = value.select(refinementBuilder).map(Slice::getContent).orElse(null);

		AttributeRange attributeRange = new AttributeRange(attributeTypeWildcard, attributeTypeProperties, possibleAttributeValues, cardinalityMin, cardinalityMax);

		BoolQueryBuilder query = refinementBuilder.getQuery();
		QueryBuilder branchCriteria = refinementBuilder.getBranchCriteria();
		boolean stated = refinementBuilder.isStated();

		if (reverse) {
			// Reverse flag

			// Fetch the relationship destination concepts
			if (possibleAttributeValues == null) {
				throw new UnsupportedOperationException("Returning the attribute values of all concepts is not supported.");
			}
			Collection<Long> destinationConceptIds = refinementBuilder.getQueryService().retrieveRelationshipDestinations(possibleAttributeValues, attributeTypesOptional.map(Slice::getContent).orElse(null), branchCriteria, stated);
			query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, destinationConceptIds));

		} else {
			// Not reverse flag

			boolean cardinalityConstraints = cardinalityMin != null || cardinalityMax != null;

			if (expressionComparisonOperator.equals("=")) {

				boolean minCardinalityIsZero = cardinalityMin != null && cardinalityMin == 0;
				// If min cardinality is zero this attribute is not required so there is a danger of selecting everything

				boolean maxCardinalityIsZero = cardinalityMax != null && cardinalityMax == 0;
				// If max cardinality is zero this attribute must not be present

				boolean isWithinGroup = parentGroup != null;
				if (isWithinGroup || (cardinalityConstraints && !maxCardinalityIsZero)) {

					// Cardinality or in-group checks required
					// We will have to check the cardinality/grouping in java.
					// Strategy:
					// - Use a temp query just to fetch the concepts
					// - Filter concepts based on cardinality/in-group
					// - Apply list of filtered concepts to main query builder
					BoolQueryBuilder tempQuery = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);

					doAddTypeAndValueCriteria(tempQuery, attributeTypeProperties, possibleAttributeValues, false);
					QueryService queryService = refinementBuilder.getQueryService();

					if (minCardinalityIsZero) {
						// With a min cardinality of zero it's likely that most concepts will adhere to the constraints so let's only collect those that don't.
						// Get concepts _not_ within cardinality constraints
						List<Long> invalidConceptIds = getConceptsWithinConstraints(Collections.singletonList(attributeRange), isWithinGroup, tempQuery, queryService, true);

						// Only match concepts _not_ on the list
						query.mustNot(termsQuery(QueryConcept.CONCEPT_ID_FIELD, invalidConceptIds));
					} else {
						// Get concepts within cardinality constraints
						List<Long> validConceptIds = getConceptsWithinConstraints(Collections.singletonList(attributeRange), isWithinGroup, tempQuery, queryService, false);

						// Only match the concepts on the list
						query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, validConceptIds));
					}
				} else {
					// Simple - just add attribute type and value criteria to main query builder
					doAddTypeAndValueCriteria(query, attributeTypeProperties, possibleAttributeValues, maxCardinalityIsZero);
				}

			} else {
				if (cardinalityConstraints) {
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

	private List<Long> getConceptsWithinConstraints(List<AttributeRange> attributeRanges, boolean withinGroup,
													BoolQueryBuilder tempQuery, QueryService queryService, boolean negateSelection) {

		// Fetch ids filtering concepts using their attribute maps and the cardinality constraints
		return ConceptSelectorHelper.fetchIds(tempQuery, null, queryConcept -> {

			boolean withinConstraints = true;

			for (AttributeRange attributeRange : attributeRanges) {

				// The cardinality and in-group filter
				// Count occurrence of this attribute
				final AtomicInteger attributeMatchCount = new AtomicInteger(0);
				final Map<Integer, AtomicInteger> groupAttributeMatchCounts = new HashMap<>();

				for (Map.Entry<Integer, Map<String, List<String>>> attributeGroup : queryConcept.getGroupedAttributesMap().entrySet()) {
					attributeGroup.getValue().entrySet().stream()
							.filter(entrySet -> attributeRange.isTypeAcceptable(entrySet.getKey()))
							.forEach((Map.Entry<String, List<String>> entrySet) -> {
								entrySet.getValue().forEach(conceptAttributeValue -> {
									if (attributeRange.isValueAcceptable(conceptAttributeValue)) {
										// Increment count for attribute match in the concept
										attributeMatchCount.incrementAndGet();
										// Increment count for attribute match in this relationship group
										groupAttributeMatchCounts.computeIfAbsent(attributeGroup.getKey(), i -> new AtomicInteger()).incrementAndGet();
									}
								});
							});
				}

				boolean attributeWithinConstraints = true;
				if (withinGroup) {
					// TODO: Attributes in group 0 but marked as grouped in the MRCM _are_ considered to be grouped in OWL and in ECL terms.
					groupAttributeMatchCounts.remove(0); // Attributes in group number 0 are not considered to be grouped.

					for (AtomicInteger inGroupAttributeMatchCount : groupAttributeMatchCounts.values()) {
						if ((attributeRange.getCardinalityMin() == null || attributeRange.getCardinalityMin() <= inGroupAttributeMatchCount.get())
								&& (attributeRange.getCardinalityMax() == null || attributeRange.getCardinalityMax() >= inGroupAttributeMatchCount.get())) {
							attributeWithinConstraints = true;
							break;
						}
					}
				} else {
					attributeWithinConstraints = (attributeRange.getCardinalityMin() == null || attributeRange.getCardinalityMin() <= attributeMatchCount.get())
							&& (attributeRange.getCardinalityMax() == null || attributeRange.getCardinalityMax() >= attributeMatchCount.get());
				}

				withinConstraints = withinConstraints && attributeWithinConstraints;
			}

			return negateSelection != withinConstraints;
		}, null, queryService).getContent();
	}

	private String getAttributeTypeField(String attributeTypeProperty) {
		return QueryConcept.ATTR_FIELD + "." + attributeTypeProperty;
	}

	static String attributeMapKeyToConceptId(String key) {
		return key.substring(QueryConcept.ATTR_FIELD.length() + 1);
	}

	public void setAttributeName(SubExpressionConstraint attributeName) {
		this.attributeName = attributeName;
	}

	public void setExpressionComparisonOperator(String expressionComparisonOperator) {
		this.expressionComparisonOperator = expressionComparisonOperator;
	}

	public void setValue(SubExpressionConstraint value) {
		this.value = value;
	}

	public void reverse() {
		this.reverse = true;
	}

	public void setCardinalityMin(int cardinalityMin) {
		this.cardinalityMin = cardinalityMin;
	}

	public void setCardinalityMax(int cardinalityMax) {
		this.cardinalityMax = cardinalityMax;
	}

	@Override
	public String toString() {
		return "EclAttribute{" +
				"attributeName=" + attributeName +
				", expressionComparisonOperator='" + expressionComparisonOperator + '\'' +
				", value=" + value +
				", reverse=" + reverse +
				", cardinalityMin=" + cardinalityMin +
				", cardinalityMax=" + cardinalityMax +
				", withinGroup=" + (parentGroup != null) +
				", groupCardinalityMin=" + (parentGroup != null ? parentGroup.getCardinalityMin() : null) +
				", groupCardinalityMax=" + (parentGroup != null ? parentGroup.getCardinalityMax() : null) +
				", withinGroup=" + (parentGroup != null) +
				'}';
	}

	public void setParentGroup(EclAttributeGroup parentGroup) {
		this.parentGroup = parentGroup;
	}

}
