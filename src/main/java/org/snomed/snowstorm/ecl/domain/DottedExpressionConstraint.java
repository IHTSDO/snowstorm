package org.snomed.snowstorm.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

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
	public Optional<Page<Long>> select(String path, QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		Optional<Page<Long>> conceptIds = super.select(path, branchCriteria, stated, conceptIdFilter, null, queryService);

		if (!conceptIds.isPresent()) {
			throw new UnsupportedOperationException("Dotted expression using wildcard focus concept is not supported.");
		}

		for (SubExpressionConstraint dottedAttribute : dottedAttributes) {
			Optional<Page<Long>> attributeTypeIdsOptional = dottedAttribute.select(path, branchCriteria, stated, conceptIdFilter, null, queryService);
			List<Long> attributeTypeIds = attributeTypeIdsOptional.map(Slice::getContent).orElse(null);
			// XXX Note that this content is not paginated
			List<Long> idList = new ArrayList<>(queryService.retrieveRelationshipDestinations(conceptIds.get().getContent(), attributeTypeIds, branchCriteria, stated));
			conceptIds = Optional.of(new PageImpl<>(idList, PageRequest.of(0, idList.size()), idList.size()));
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
