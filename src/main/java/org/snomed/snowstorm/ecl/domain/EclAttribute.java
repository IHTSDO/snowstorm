package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;

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
	private Integer cardinalityMin;
	private Integer cardinalityMax;

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		// Input validation
		if (cardinalityMin != null && cardinalityMax != null && cardinalityMin > cardinalityMax) {
			throw new IllegalArgumentException("Within cardinality constraints the minimum must not be greater than the maximum.");
		}
		if (cardinalityMin != null && cardinalityMin == 0 && cardinalityMax == null) {
			// [0..*] means this constraint is meaningless
			return;
		}

		Collection<Long> attributeTypes = attributeName.select(path, branchCriteria, stated, null, queryService);

		boolean attributeTypeWildcard = attributeTypes == null;
		Set<String> attributeTypeProperties;
		if (attributeTypeWildcard) {
			attributeTypeProperties = Collections.singleton(QueryConcept.ATTR_TYPE_WILDCARD);
		} else {
			attributeTypeProperties = attributeTypes.stream().map(Object::toString).collect(Collectors.toSet());
			if (attributeTypes.isEmpty()) {
				// Attribute type is not a wildcard but empty selection
				// Force query to return nothing
				attributeTypeProperties.add(ExpressionConstraint.MISSING);
			}
		}

		Collection<Long> possibleAttributeValues = value.select(path, branchCriteria, stated, null, queryService);

		if (reverse) {
			// Reverse flag

			// Fetch the relationship destination concepts
			if (possibleAttributeValues == null) {
				throw new UnsupportedOperationException("Returning the attribute values of all concepts is not supported.");
			}
			Collection<Long> destinationConceptIds = queryService.retrieveRelationshipDestinations(possibleAttributeValues, attributeTypes, branchCriteria, stated);
			query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, destinationConceptIds));

		} else {
			// Not reverse flag

			boolean cardinalityConstraints = cardinalityMin != null || cardinalityMax != null;

			if (expressionComparisonOperator.equals("=")) {

				boolean minCardinalityIsZero = cardinalityMin != null && cardinalityMin == 0;
				// If min cardinality is zero this attribute is not required so there is a danger of selecting everything

				boolean maxCardinalityIsZero = cardinalityMax != null && cardinalityMax == 0;
				// If max cardinality is zero this attribute must not be present

				if (cardinalityConstraints && !maxCardinalityIsZero) {

					// Cardinality checks required
					// We will have to check the cardinality in java.
					// Strategy:
					// - Use a temp query just to fetch the concepts
					// - Filter concepts based on cardinality
					// - Apply list of filtered concepts to main query builder
					BoolQueryBuilder tempQuery = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);

					doAddTypeAndValueCriteria(tempQuery, attributeTypeProperties, possibleAttributeValues, false);

					if (minCardinalityIsZero) {
						// With a min cardinality of zero it's likely that most concepts will adhere to the constraints so let's only collect those that don't.
						// Get concepts _not_ within cardinality constraints
						List<Long> invalidConceptIds = getConceptsWithinCardinalityConstraints(attributeTypeProperties, attributeTypeWildcard, possibleAttributeValues,
								tempQuery, queryService, true);

						// Only match concepts _not_ on the list
						query.mustNot(termsQuery(QueryConcept.CONCEPT_ID_FIELD, invalidConceptIds));
					} else {
						// Get concepts within cardinality constraints
						List<Long> validConceptIds = getConceptsWithinCardinalityConstraints(attributeTypeProperties, attributeTypeWildcard, possibleAttributeValues,
								tempQuery, queryService, false);

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

	private void doAddTypeAndValueCriteria(BoolQueryBuilder query, Set<String> attributeTypeProperties, Collection<Long> possibleAttributeValues, boolean maxCardinalityIsZero) {
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

	private List<Long> getConceptsWithinCardinalityConstraints(Set<String> attributeTypeProperties, boolean attributeTypeWildcard, Collection<Long> possibleAttributeValues,
															   BoolQueryBuilder tempQuery, QueryService queryService, boolean negateSelection) {

		// Fetch ids filtering concepts using their attribute maps and the cardinality constraints
		return ConceptSelectorHelper.fetchIds(tempQuery, null, queryConcept -> {
			// The cardinality filter
			// Count occurrence of this attribute
			AtomicInteger attributeMatchCount = new AtomicInteger(0);
			for (Map<String, List<String>> groupAttributes : queryConcept.getGroupedAttributesMap().values()) {
				groupAttributes.entrySet().stream()
						.filter(entrySet -> attributeTypeWildcard || attributeTypeProperties.contains(entrySet.getKey()))
						.forEach(entrySet -> {
							entrySet.getValue().forEach(conceptAttributeValue -> {
								if (possibleAttributeValues == null || possibleAttributeValues.contains(parseLong(conceptAttributeValue))) {
									attributeMatchCount.incrementAndGet();
								}
							});
						});
			}

			// Return true if the concept attribute count is within the constraints
			boolean withinConstraints = (cardinalityMin == null || cardinalityMin <= attributeMatchCount.get()) && (cardinalityMax == null || cardinalityMax >= attributeMatchCount.get());
			return negateSelection != withinConstraints;
		}, queryService);
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

}
