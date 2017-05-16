package org.ihtsdo.elasticsnomed.core.data.domain.review;

public enum ReviewStatus {

	/** New, changed and detached concepts are still being collected. */
	PENDING,

	/** Changes are available, no commits have happened since the start of the review. */
	CURRENT,

	/** Computed differences are not up-to-date; a commit on either of the compared branches invalidated it. */
	STALE,

	/** Differences could not be computed for some reason. */
	FAILED
}
