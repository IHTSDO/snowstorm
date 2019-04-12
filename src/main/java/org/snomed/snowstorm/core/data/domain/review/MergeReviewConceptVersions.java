package org.snomed.snowstorm.core.data.domain.review;

import org.snomed.snowstorm.core.data.domain.Concept;

public class MergeReviewConceptVersions {

	private Concept sourceConcept;
	private Concept targetConcept;
	private Concept autoMergedConcept;
	private Concept manuallyMergedConcept;

	public MergeReviewConceptVersions(Concept sourceConcept, Concept targetConcept) {
		this.sourceConcept = sourceConcept;
		this.targetConcept = targetConcept;
	}

	public Concept getSourceConcept() {
		return sourceConcept;
	}

	public void setSourceConcept(Concept sourceConcept) {
		this.sourceConcept = sourceConcept;
	}

	public Concept getTargetConcept() {
		return targetConcept;
	}

	public void setTargetConcept(Concept targetConcept) {
		this.targetConcept = targetConcept;
	}

	public Concept getAutoMergedConcept() {
		return autoMergedConcept;
	}

	public void setAutoMergedConcept(Concept autoMergedConcept) {
		this.autoMergedConcept = autoMergedConcept;
	}

	public Concept getManuallyMergedConcept() {
		return manuallyMergedConcept;
	}

	public void setManuallyMergedConcept(Concept manuallyMergedConcept) {
		this.manuallyMergedConcept = manuallyMergedConcept;
	}

	@Override
	public String toString() {
		return "MergeReviewConceptVersions{" +
				"sourceConcept=" + sourceConcept +
				", targetConcept=" + targetConcept +
				", autoMergedConcept=" + autoMergedConcept +
				", manuallyMergedConcept=" + manuallyMergedConcept +
				'}';
	}
}
