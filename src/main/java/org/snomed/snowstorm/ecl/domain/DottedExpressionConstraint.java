package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;

import java.util.*;

public class DottedExpressionConstraint extends ExpressionConstraint {

	private final SubExpressionConstraint subExpressionConstraint;
	private final List<SubExpressionConstraint> dottedAttributes;

	public DottedExpressionConstraint(SubExpressionConstraint subExpressionConstraint) {
		this.subExpressionConstraint = subExpressionConstraint;
		dottedAttributes = new ArrayList<>();
	}

	@Override
	protected boolean isWildcard() {
		return false;
	}

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		subExpressionConstraint.addCriteria(query, path, branchCriteria, stated, queryService);
	}

	@Override
	public Optional<List<Long>> select(String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, QueryService queryService) {
		Optional<List<Long>> conceptIds = super.select(path, branchCriteria, stated, conceptIdFilter, queryService);

		if (!conceptIds.isPresent()) {
			throw new UnsupportedOperationException("Dotted expression using wildcard focus concept is not supported.");
		}

		for (SubExpressionConstraint dottedAttribute : dottedAttributes) {
			Optional<List<Long>> attributeTypeIds = dottedAttribute.select(path, branchCriteria, stated, conceptIdFilter, queryService);
			conceptIds = Optional.of(new ArrayList<>(queryService.retrieveRelationshipDestinations(conceptIds.get(), attributeTypeIds.orElse(null), branchCriteria, stated)));
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
