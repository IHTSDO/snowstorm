package org.ihtsdo.elasticsnomed.ecl.domain;

public class EclAttributeSet {

	private SubAttributeSet subAttributeSet;
//	private List<SubAttributeSet> conjunctionAttributeSets;
//	private List<SubAttributeSet> disjunctionAttributeSets;

	public EclAttributeSet() {
//		conjunctionAttributeSets = new ArrayList<>();
//		disjunctionAttributeSets = new ArrayList<>();
	}

	public void setSubAttributeSet(SubAttributeSet subAttributeSet) {
		this.subAttributeSet = subAttributeSet;
	}

	public SubAttributeSet getSubAttributeSet() {
		return subAttributeSet;
	}
//
//	public void addConjunctionAttributeSet(SubAttributeSet subAttributeSet) {
//		conjunctionAttributeSets.add(subAttributeSet);
//	}
//
//	public void addDisjunctionAttributeSet(SubAttributeSet subAttributeSet) {
//		disjunctionAttributeSets.add(subAttributeSet);
//	}
}
