package org.snomed.snowstorm.core.data.domain.review;

import org.snomed.snowstorm.core.data.domain.Concept;

import java.util.HashMap;
import java.util.Map;

public class MergeReview {

	private final String id;
	private final String sourcePath;
	private final String targetPath;
	private final String sourceToTargetReviewId;
	private final String targetToSourceReviewId;
	private final Map<Long, Concept> manuallyMergedConcepts;

	private ReviewStatus status;

	public MergeReview(String id, String sourcePath, String targetPath, String sourceToTargetReviewId, String targetToSourceReviewId) {
		this.id = id;
		this.sourcePath = sourcePath;
		this.targetPath = targetPath;
		this.sourceToTargetReviewId = sourceToTargetReviewId;
		this.targetToSourceReviewId = targetToSourceReviewId;
		manuallyMergedConcepts = new HashMap<>();
	}

	public void putManuallyMergedConcept(Concept manuallyMergedConcept) {
		manuallyMergedConcepts.put(manuallyMergedConcept.getConceptIdAsLong(), manuallyMergedConcept);
	}

	public void putManuallyMergedConceptDeletion(Long conceptId) {
		Concept concept = new Concept(conceptId.toString());
		concept.markDeleted();
		manuallyMergedConcepts.put(conceptId, concept);
	}

	public ReviewStatus getStatus() {
		return status;
	}

	public void setStatus(ReviewStatus status) {
		this.status = status;
	}

	public String getId() {
		return id;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public String getTargetPath() {
		return targetPath;
	}

	public String getSourceToTargetReviewId() {
		return sourceToTargetReviewId;
	}

	public String getTargetToSourceReviewId() {
		return targetToSourceReviewId;
	}

	public Map<Long, Concept> getManuallyMergedConcepts() {
		return manuallyMergedConcepts;
	}

}
