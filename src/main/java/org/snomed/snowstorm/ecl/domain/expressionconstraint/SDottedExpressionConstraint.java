package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.snomed.langauges.ecl.domain.expressionconstraint.DottedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.util.PageCollectionUtil;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class SDottedExpressionConstraint extends DottedExpressionConstraint implements SExpressionConstraint {
	public SDottedExpressionConstraint(SubExpressionConstraint subExpressionConstraint) {
		super(subExpressionConstraint);
	}

	@Override
	public Optional<Page<Long>> select(String path, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {
		Optional<Page<Long>> conceptIds = SExpressionConstraintHelper.select(this, path, branchCriteria, stated, conceptIdFilter, null, queryService);

		if (!conceptIds.isPresent()) {
			throw new UnsupportedOperationException("Dotted expression using wildcard focus concept is not supported.");
		}

		for (SubExpressionConstraint dottedAttribute : dottedAttributes) {
			Optional<Page<Long>> attributeTypeIdsOptional = ((SSubExpressionConstraint)dottedAttribute).select(path, branchCriteria, stated, conceptIdFilter, null, queryService);
			List<Long> attributeTypeIds = attributeTypeIdsOptional.map(Slice::getContent).orElse(null);
			// XXX Note that this content is not paginated
			List<Long> idList = new ArrayList<>(queryService.findRelationshipDestinationIds(conceptIds.get().getContent(), attributeTypeIds, branchCriteria, stated));
			conceptIds = Optional.of(new PageImpl<>(idList));
		}

		// Manually apply pagination
		if (pageRequest != null) {
			List<Long> content = conceptIds.get().getContent();
			List<Long> pageOfContent = PageCollectionUtil.subList(content, pageRequest.getPageNumber(), pageRequest.getPageSize());
			conceptIds = Optional.of(new PageImpl<>(pageOfContent, pageRequest, content.size()));
		}

		return conceptIds;
	}

	@Override
	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		return select(refinementBuilder.getPath(), refinementBuilder.getBranchCriteria(), refinementBuilder.isStated(), null, null, refinementBuilder.getQueryService());
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		((SSubExpressionConstraint)subExpressionConstraint).addCriteria(refinementBuilder);
	}

}
