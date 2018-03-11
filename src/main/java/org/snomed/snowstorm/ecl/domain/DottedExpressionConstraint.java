package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DottedExpressionConstraint extends ExpressionConstraint {

	private final SubExpressionConstraint subExpressionConstraint;
	private final List<SubExpressionConstraint> dottedAttributes;

	public DottedExpressionConstraint(SubExpressionConstraint subExpressionConstraint) {
		this.subExpressionConstraint = subExpressionConstraint;
		dottedAttributes = new ArrayList<>();
	}

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		subExpressionConstraint.addCriteria(query, path, branchCriteria, stated, queryService);
	}

	@Override
	public List<Long> select(String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, QueryService queryService) {
		List<Long> conceptIds = super.select(path, branchCriteria, stated, conceptIdFilter, queryService);

		for (SubExpressionConstraint dottedAttribute : dottedAttributes) {
			List<Long> attributeTypeIds = dottedAttribute.select(path, branchCriteria, stated, conceptIdFilter, queryService);
			conceptIds = new ArrayList<>(queryService.retrieveRelationshipDestinations(conceptIds, attributeTypeIds, branchCriteria, stated));
		}

		return conceptIds;
	}

	public void addDottedAttribute(SubExpressionConstraint dottedAttribute) {
		dottedAttributes.add(dottedAttribute);
	}

	@Override
	public String toString() {
		return "DottedExpressionConstraint{" +
				"subExpressionConstraint=" + subExpressionConstraint +
				", dottedAttributes=" + dottedAttributes +
				'}';
	}
}
