package org.snomed.snowstorm.ecl.validation;

import com.google.common.base.Functions;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeGroup;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SCompoundExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SRefinedExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.refinement.SEclAttribute;
import org.snomed.snowstorm.ecl.domain.refinement.SEclAttributeSet;
import org.snomed.snowstorm.ecl.domain.refinement.SSubAttributeSet;
import org.snomed.snowstorm.ecl.domain.refinement.SSubRefinement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.data.domain.Concepts.CONCEPT_MODEL_DATA_ATTRIBUTE;
import static org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraintHelper.MISSING;

@Service
public class ECLPreprocessingService implements CommitListener {

	private static final String RETURN_ALL_CONCRETE_ATTRIBUTES_ECL_QUERY = "< " + CONCEPT_MODEL_DATA_ATTRIBUTE;

	private static final Map<String, List<String>> CACHED_CONCRETE_CONCEPT_IDS = new HashMap<>();

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Override
	public final void preCommitCompletion(final Commit commit) throws IllegalStateException {
		CACHED_CONCRETE_CONCEPT_IDS.remove(commit.getBranch().getPath());
	}

	public SExpressionConstraint replaceIncorrectConcreteAttributeValue(final SExpressionConstraint sExpressionConstraint, final String branch, final PageRequest pageRequest) {
		if (sExpressionConstraint != null) {
			if (!CACHED_CONCRETE_CONCEPT_IDS.containsKey(branch) || (CACHED_CONCRETE_CONCEPT_IDS.containsKey(branch) && CACHED_CONCRETE_CONCEPT_IDS.get(branch).isEmpty())) {
				cacheConcreteConceptIds(branch);
			}
			iterateOverExpressionInstance(sExpressionConstraint, branch);
		}
		return sExpressionConstraint;
	}

	private void iterateOverExpressionInstance(final SExpressionConstraint sExpressionConstraint, final String branch) {
		if (sExpressionConstraint instanceof SRefinedExpressionConstraint) {
			processSRefinedExpressionConstraints((SRefinedExpressionConstraint) sExpressionConstraint, branch);
		} else if (sExpressionConstraint instanceof SCompoundExpressionConstraint) {
			processSCompoundExpressionConstraints((SCompoundExpressionConstraint) sExpressionConstraint, branch);
		}
	}

	private void processSCompoundExpressionConstraints(final SCompoundExpressionConstraint sCompoundExpressionConstraint, final String branch) {
		if (sCompoundExpressionConstraint != null) {
			final List<SubExpressionConstraint> subConjunctionExpressionConstraints = sCompoundExpressionConstraint.getConjunctionExpressionConstraints();
			if (subConjunctionExpressionConstraints != null) {
				subConjunctionExpressionConstraints.forEach(subExpressionConstraint ->
						iterateOverExpressionInstance((SExpressionConstraint) subExpressionConstraint.getNestedExpressionConstraint(), branch));
			}
			final List<SubExpressionConstraint> subDisjunctionExpressionConstraints = sCompoundExpressionConstraint.getDisjunctionExpressionConstraints();
			if (subDisjunctionExpressionConstraints != null) {
				subDisjunctionExpressionConstraints.forEach(subExpressionConstraint ->
						iterateOverExpressionInstance((SExpressionConstraint) subExpressionConstraint.getNestedExpressionConstraint(), branch));
			}
		}
	}

	private void processSRefinedExpressionConstraints(final SRefinedExpressionConstraint sExpressionConstraint, final String branch) {
		extractSEclAttributes(sExpressionConstraint).stream().filter(Objects::nonNull).filter(SEclAttribute::isConcreteValueQuery)
				.forEach(sEclAttribute -> sEclAttribute.getConceptIds().stream().filter(concreteConceptId -> CACHED_CONCRETE_CONCEPT_IDS.containsKey(branch)
						&& !CACHED_CONCRETE_CONCEPT_IDS.get(branch).contains(concreteConceptId))
						.forEach(concreteConceptId -> setPlaceholder(sEclAttribute)));
	}

	private void setPlaceholder(final SEclAttribute sEclAttribute) {
		if (sEclAttribute.getNumericValue() != null) {
			sEclAttribute.setNumericValue(String.valueOf(Float.MAX_VALUE));
		} else if (sEclAttribute.getStringValue() != null) {
			sEclAttribute.setStringValue(MISSING);
		}
	}

	private List<SEclAttribute> extractSEclAttributes(final SRefinedExpressionConstraint expressionConstraint) {
		final List<SEclAttribute> sEclAttributes = new ArrayList<>();
		final EclRefinement eclRefinement = expressionConstraint.getEclRefinement();
		if (eclRefinement != null) {
			iterateOverEclRefinement(eclRefinement, sEclAttributes);
		}
		return sEclAttributes;
	}

	private void iterateOverEclRefinement(final EclRefinement eclRefinement, final List<SEclAttribute> sEclAttributes) {
		final List<SubRefinement> conjunctionSubRefinements = eclRefinement.getConjunctionSubRefinements();
		if (conjunctionSubRefinements != null) {
			conjunctionSubRefinements.forEach(subRefinement -> extractSEclAttributes(subRefinement, sEclAttributes));
		}
		final List<SubRefinement> disjunctionSubRefinements = eclRefinement.getDisjunctionSubRefinements();
		if (disjunctionSubRefinements != null) {
			disjunctionSubRefinements.forEach(subRefinement -> extractSEclAttributes(subRefinement, sEclAttributes));
		}
		extractSEclAttributes(eclRefinement.getSubRefinement(), sEclAttributes);
	}

	private void extractSEclAttributes(final SubRefinement subRefinement, List<SEclAttribute> sEclAttributes) {
		if (subRefinement != null) {
			extractSEclAttributes((SSubRefinement) subRefinement, sEclAttributes);
			final EclRefinement eclRefinement = subRefinement.getEclRefinement();
			if (eclRefinement != null) {
				iterateOverEclRefinement(eclRefinement, sEclAttributes);
			}
		}
	}

	private void extractSEclAttributes(final SSubRefinement subRefinement, final List<SEclAttribute> sEclAttributes) {
		final EclAttributeGroup eclAttributeGroup = subRefinement.getEclAttributeGroup();
		final SEclAttributeSet sEclAttributeSet = (SEclAttributeSet) subRefinement.getEclAttributeSet();
		if (eclAttributeGroup != null) {
			doExtractSEclAttributes(sEclAttributes, (SEclAttributeSet) eclAttributeGroup.getAttributeSet());
		} else {
			doExtractSEclAttributes(sEclAttributes, sEclAttributeSet);
		}
	}

	private void doExtractSEclAttributes(final List<SEclAttribute> sEclAttributes, final SEclAttributeSet sEclAttributeSet) {
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

	private void cacheConcreteConceptIds(final String branch) {
		CACHED_CONCRETE_CONCEPT_IDS.put(branch, eclQueryService.doSelectConceptIds(RETURN_ALL_CONCRETE_ATTRIBUTES_ECL_QUERY,
				versionControlHelper.getBranchCriteria(branch), branch, false, null, PageRequest.of(0, 100), null)
				.toList().stream().map(Functions.toStringFunction()).collect(Collectors.toList()));
	}
}
