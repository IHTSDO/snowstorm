package org.snomed.snowstorm.core.data.domain.classification;

public enum ClassificationStatus {

	SCHEDULED, RUNNING, FAILED, COMPLETED(true), SAVED(true), SAVE_FAILED(true);

	boolean resultsAvailable;

	ClassificationStatus(boolean resultsAvailable) {
		this.resultsAvailable = resultsAvailable;
	}

	ClassificationStatus() {
		this(false);
	}

	public boolean isResultsAvailable() {
		return resultsAvailable;
	}
}
