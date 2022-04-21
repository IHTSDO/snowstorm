package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import io.kaicode.elasticvc.api.BranchCriteria;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.snomed.langauges.ecl.domain.expressionconstraint.DottedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.ecl.ConceptSelectorHelper;
import org.snomed.snowstorm.ecl.ECLContentService;
import org.snomed.snowstorm.ecl.deserializer.ECLModelDeserializer;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.stream.Collectors.toSet;

public class SDottedExpressionConstraint extends DottedExpressionConstraint implements SExpressionConstraint {

	@SuppressWarnings("unused")
	// For JSON
	private SDottedExpressionConstraint() {
		this(null);
	}

	public SDottedExpressionConstraint(SubExpressionConstraint subExpressionConstraint) {
		super(subExpressionConstraint);
	}


	@Override
	public Optional<Page<Long>> select(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter,
			PageRequest pageRequest, ECLContentService eclContentService, boolean triedCache) {

		triedCache = false;// The subExpressionConstraint has not been through the cache

		// Concept ids filtering should be done on attribute values for dot notation ECL query
		// Fetch source concept ids
		if (getSubExpressionConstraint().isWildcard()) {
			throw new UnsupportedOperationException("Dotted expression using wildcard focus concept is not supported.");
		}
		List<Long> conceptIds = ConceptSelectorHelper.select((SExpressionConstraint) getSubExpressionConstraint(), branchCriteria, stated,
				null, null, eclContentService, triedCache).getContent();

		// Iteratively traverse attributes
		for (SubExpressionConstraint dottedAttribute : dottedAttributes) {
			SSubExpressionConstraint aDottedAttribute = (SSubExpressionConstraint) dottedAttribute;
			Optional<Page<Long>> attributeTypeIdsOptional = aDottedAttribute.select(branchCriteria, stated, null, null, eclContentService, false);
			List<Long> attributeTypeIds = attributeTypeIdsOptional.map(Slice::getContent).orElse(null);
			// XXX Note that this content is not paginated
			conceptIds = eclContentService.findRelationshipDestinationIds(conceptIds, attributeTypeIds, branchCriteria, stated);
		}

		LongPredicate filter = null;
		if (conceptIdFilter != null && !conceptIdFilter.isEmpty()) {
			LongOpenHashSet fastSet = new LongOpenHashSet(conceptIdFilter);
			filter = fastSet::contains;
		}
		// Filtering on final results
		if (filter != null) {
			conceptIds = conceptIds.stream().filter(filter::test).collect(Collectors.toList());
		}

		// Manually apply pagination
		Optional<Page<Long>> pageResults;
		if (pageRequest != null) {
			List<Long> pageOfContent = PageHelper.subList(conceptIds, pageRequest.getPageNumber(), pageRequest.getPageSize());
			pageResults = Optional.of(new PageImpl<>(pageOfContent, pageRequest, conceptIds.size()));
		} else {
			pageResults = Optional.of(new PageImpl<>(conceptIds));
		}
		return pageResults;
	}

	@Override
	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		return select(refinementBuilder.getBranchCriteria(), refinementBuilder.isStated(), null, null, refinementBuilder.getEclContentService(), false);
	}

	@Override
	public Set<String> getConceptIds() {
		Set<String> conceptIds = newHashSet();
		conceptIds.addAll(((SSubExpressionConstraint) subExpressionConstraint).getConceptIds());
		Set<String> dottedAttributesConceptIds = dottedAttributes.stream()
				.map(SSubExpressionConstraint.class::cast)
				.map(SSubExpressionConstraint::getConceptIds)
				.flatMap(Set::stream)
				.collect(toSet());
		conceptIds.addAll(dottedAttributesConceptIds);
		return conceptIds;
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder, Consumer<List<Long>> filteredOrSupplementedContentCallback, boolean triedCache) {
		((SSubExpressionConstraint)subExpressionConstraint).addCriteria(refinementBuilder, (ids) -> {}, triedCache);
	}

	@Override
	public String toEclString() {
		return toString(new StringBuffer()).toString();
	}

	public StringBuffer toString(StringBuffer buffer) {
		ECLModelDeserializer.expressionConstraintToString(subExpressionConstraint, buffer);
		for (SubExpressionConstraint dottedAttribute : dottedAttributes) {
			buffer.append(" . ");
			ECLModelDeserializer.expressionConstraintToString(dottedAttribute, buffer);
		}
		return buffer;
	}
}
