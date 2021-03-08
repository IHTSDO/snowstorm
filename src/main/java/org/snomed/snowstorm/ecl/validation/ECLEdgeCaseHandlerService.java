package org.snomed.snowstorm.ecl.validation;

import com.google.common.base.Functions;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SRefinedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.refinement.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.data.domain.Concepts.CONCEPT_MODEL_DATA_ATTRIBUTE;
import static org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraintHelper.MISSING;

@Service
public class ECLEdgeCaseHandlerService implements CommitListener {

	private static final String RETURN_ALL_CONCRETE_ATTRIBUTES_ECL_QUERY = "< " + CONCEPT_MODEL_DATA_ATTRIBUTE;

	private static final List<String> CACHED_CONCRETE_CONCEPT_IDS = new ArrayList<>();

	@Autowired
	private ECLQueryBuilder eclQueryBuilder;

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Override
	public final void preCommitCompletion(final Commit commit) throws IllegalStateException {
		CACHED_CONCRETE_CONCEPT_IDS.clear();
	}

	public SExpressionConstraint replaceIncorrectConcreteAttributeValue(final String ecl, final String branch, final PageRequest pageRequest) {
		SExpressionConstraint expressionConstraint = null;
		if (ecl != null) {
			if (CACHED_CONCRETE_CONCEPT_IDS.isEmpty()) {
				cacheConcreteConceptIds(branch, pageRequest);
			}
			expressionConstraint = (SExpressionConstraint) eclQueryBuilder.createQuery(ecl);
			if (expressionConstraint instanceof SRefinedExpressionConstraint) {
				extractSEclAttributes((SRefinedExpressionConstraint) expressionConstraint).stream().filter(Objects::nonNull).filter(SEclAttribute::isConcreteValueQuery)
						.forEach(sEclAttribute -> sEclAttribute.getConceptIds().stream().filter(concreteConceptId -> !CACHED_CONCRETE_CONCEPT_IDS.contains(concreteConceptId))
								.forEach(concreteConceptId -> setPlaceholder(sEclAttribute)));
			}
		}
		return expressionConstraint;
	}

	private void setPlaceholder(final SEclAttribute sEclAttribute) {
		if (sEclAttribute.getNumericValue() != null) {
			sEclAttribute.setNumericValue(String.valueOf(Float.MAX_VALUE));
		} else if (sEclAttribute.getStringValue() != null) {
			sEclAttribute.setStringValue(MISSING);
		}
	}

	private List<SEclAttribute> extractSEclAttributes(final SRefinedExpressionConstraint expressionConstraint) {
		final SEclRefinement sEclRefinement = (SEclRefinement) expressionConstraint.getEclRefinement();
		final SSubRefinement subRefinement = (SSubRefinement) sEclRefinement.getSubRefinement();
		SEclAttributeSet sEclAttributeSet;
		final List<SEclAttribute> sEclAttributes = new ArrayList<>();
		if ((sEclAttributeSet = (SEclAttributeSet) subRefinement.getEclAttributeSet()) == null) {
			sEclAttributeSet = (SEclAttributeSet) subRefinement.getEclAttributeGroup().getAttributeSet();
			final List<SubAttributeSet> conjunctionAttributeSets = sEclAttributeSet.getConjunctionAttributeSet();
			final List<SubAttributeSet> disjunctionAttributeSets = sEclAttributeSet.getDisjunctionAttributeSet();
			if (conjunctionAttributeSets != null || disjunctionAttributeSets != null) {
				final List<SubAttributeSet> subAttributeSets = new ArrayList<>(conjunctionAttributeSets == null
						? Collections.emptyList() : conjunctionAttributeSets);
				subAttributeSets.addAll(disjunctionAttributeSets == null ? Collections.emptyList() : disjunctionAttributeSets);
				getSEclAttributes(subAttributeSets).stream().filter(Objects::nonNull).forEach(sEclAttributes::add);
			} else {
				sEclAttributes.addAll(getSEclAttributes(Collections.singletonList(sEclAttributeSet.getSubAttributeSet())));
			}
		} else {
			sEclAttributes.addAll(getSEclAttributes(Collections.singletonList(sEclAttributeSet.getSubAttributeSet())));
		}
		return sEclAttributes;
	}

	private List<SEclAttribute> getSEclAttributes(final List<SubAttributeSet> subAttributeSets) {
		final List<SEclAttribute> sEclAttributes = new ArrayList<>();
		for (final SubAttributeSet subAttributeSet : subAttributeSets) {
			final SSubAttributeSet sSubAttributeSet = (SSubAttributeSet) subAttributeSet;
			final SEclAttribute sEclAttribute = (SEclAttribute) sSubAttributeSet.getAttribute();
			if (sEclAttribute != null) {
				final SSubExpressionConstraint sSubExpressionConstraint = (SSubExpressionConstraint) sEclAttribute.getAttributeName();
				if (sSubExpressionConstraint != null && sSubExpressionConstraint.isWildcard()) {
					return Collections.emptyList();
				}
			}
			sEclAttributes.add(sEclAttribute);
		}
		return sEclAttributes;
	}

	private void cacheConcreteConceptIds(final String branch, final PageRequest pageRequest) {
		CACHED_CONCRETE_CONCEPT_IDS.addAll(eclQueryService.selectRelevantConceptIds(RETURN_ALL_CONCRETE_ATTRIBUTES_ECL_QUERY,
				versionControlHelper.getBranchCriteria(branch), branch, false, null, pageRequest, null)
				.toList().stream().map(Functions.toStringFunction()).collect(Collectors.toList()));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ECLEdgeCaseHandlerService that = (ECLEdgeCaseHandlerService) o;
		return Objects.equals(eclQueryBuilder, that.eclQueryBuilder) &&
				Objects.equals(eclQueryService, that.eclQueryService) &&
				Objects.equals(versionControlHelper, that.versionControlHelper);
	}

	@Override
	public int hashCode() {
		return Objects.hash(eclQueryBuilder, eclQueryService, versionControlHelper);
	}
}
