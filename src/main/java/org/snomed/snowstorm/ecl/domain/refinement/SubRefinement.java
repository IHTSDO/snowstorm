package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.snowstorm.ecl.domain.RefinementBuilder;

public class SubRefinement implements Refinement {

	private EclAttributeSet eclAttributeSet;
	private EclAttributeGroup eclAttributeGroup;
	private EclRefinement eclRefinement;

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		if (eclAttributeSet != null) {
			eclAttributeSet.addCriteria(refinementBuilder);
		} else if (eclAttributeGroup != null) {
			eclAttributeGroup.addCriteria(refinementBuilder);
		} else {
			eclRefinement.addCriteria(refinementBuilder);
		}
	}

	public void setEclAttributeSet(EclAttributeSet eclAttributeSet) {
		this.eclAttributeSet = eclAttributeSet;
	}

	public void setEclAttributeGroup(EclAttributeGroup eclAttributeGroup) {
		this.eclAttributeGroup = eclAttributeGroup;
	}

	public void setEclRefinement(EclRefinement eclRefinement) {
		this.eclRefinement = eclRefinement;
	}

	@Override
	public String toString() {
		return "SubRefinement{" +
				"eclAttributeSet=" + eclAttributeSet +
				", eclAttributeGroup=" + eclAttributeGroup +
				", eclRefinement=" + eclRefinement +
				'}';
	}
}
