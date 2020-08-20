package org.snomed.snowstorm.ecl.domain.refinement;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.SRefinement;
import org.snomed.snowstorm.ecl.domain.SubRefinementBuilder;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.MatchContext;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.stream.Collectors.toSet;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.springframework.util.CollectionUtils.isEmpty;

public class SEclRefinement extends EclRefinement implements SRefinement {

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		((SSubRefinement)subRefinement).addCriteria(refinementBuilder);

		if (conjunctionSubRefinements != null) {
			for (SubRefinement conjunctionSubRefinement : conjunctionSubRefinements) {
				((SSubRefinement)conjunctionSubRefinement).addCriteria(refinementBuilder);
			}
		}
		if (disjunctionSubRefinements != null && !disjunctionSubRefinements.isEmpty()) {
			BoolQueryBuilder shouldQueries = boolQuery();
			refinementBuilder.getQuery().must(shouldQueries);
			for (SubRefinement disjunctionSubRefinement : disjunctionSubRefinements) {
				BoolQueryBuilder shouldQuery = boolQuery();
				shouldQueries.should(shouldQuery);
				((SSubRefinement)disjunctionSubRefinement).addCriteria(new SubRefinementBuilder(refinementBuilder, shouldQuery));
			}
		}
	}

	@Override
	public Set<String> getConceptIds() {
		Set<String> conceptIds = newHashSet();
		if (subRefinement != null) {
			conceptIds.addAll(((SSubRefinement) subRefinement).getConceptIds());
		}
		if (!isEmpty(conjunctionSubRefinements)) {
			conceptIds.addAll(getConceptIds(conjunctionSubRefinements));
		}
		if (!isEmpty(disjunctionSubRefinements)) {
			conceptIds.addAll(getConceptIds(disjunctionSubRefinements));
		}
		return conceptIds;
	}

	private Set<String> getConceptIds(List<SubRefinement> subRefinements) {
		return subRefinements.stream()
				.map(SSubRefinement.class::cast)
				.map(SSubRefinement::getConceptIds)
				.flatMap(Set::stream)
				.collect(toSet());
	}

	public boolean isMatch(MatchContext matchContext) {
		boolean conceptMatches = ((SSubRefinement) subRefinement).isMatch(matchContext);
		if (conjunctionSubRefinements != null) {
			for (SubRefinement conjunctionSubRefinement : conjunctionSubRefinements) {
				conceptMatches = conceptMatches && ((SSubRefinement) conjunctionSubRefinement).isMatch(matchContext);
			}
		}
		if (disjunctionSubRefinements != null) {
			boolean oneMatches = false;
			for (SubRefinement disjunctionSubRefinement : disjunctionSubRefinements) {
				if (((SSubRefinement) disjunctionSubRefinement).isMatch(matchContext)) {
					oneMatches = true;
				}
			}
			conceptMatches = conceptMatches || oneMatches;
		}
		return conceptMatches;
	}
}
