package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class EclAttribute implements Refinement {

	private SubExpressionConstraint attributeName;
	private String expressionComparisonOperator;
	private SubExpressionConstraint value;

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		Collection<Long> attributeTypes = attributeName.select(path, branchCriteria, stated, null, queryService);

		Set<String> attributeTypeProperties;
		if (attributeTypes == null) {
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
		if (expressionComparisonOperator.equals("=")) {
			if (possibleAttributeValues == null) {
				// Value is wildcard, attribute just needs to exist
				for (String attributeTypeProperty : attributeTypeProperties) {
					query.must(existsQuery(getAttributeTypeField(attributeTypeProperty)));
				}
			} else {
				if (possibleAttributeValues.isEmpty()) {
					// Attribute value is not a wildcard but empty selection
					// Force query to return nothing
					query.must(termQuery("force-nothing", "true"));
				} else {
					BoolQueryBuilder shoulds = boolQuery();
					query.must(shoulds);
					for (String attributeTypeProperty : attributeTypeProperties) {
						shoulds.should(termsQuery(getAttributeTypeField(attributeTypeProperty), possibleAttributeValues));
					}
				}
			}
		} else {
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

}
