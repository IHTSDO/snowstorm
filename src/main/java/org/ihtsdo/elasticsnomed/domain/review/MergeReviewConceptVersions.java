package org.ihtsdo.elasticsnomed.domain.review;

import org.ihtsdo.elasticsnomed.domain.Concept;

public class MergeReviewConceptVersions {

	private Concept sourceConcept;
	private Concept targetConcept;
	private Concept autoMergedConcept;
	private Concept manuallyMergedConcept;

	public MergeReviewConceptVersions(Concept sourceConcept) {
		this.sourceConcept = sourceConcept;
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
}
