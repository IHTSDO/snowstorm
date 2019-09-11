package org.snomed.snowstorm.core.data.services.pojo;

public class AuthoringStatsSummary {
	private long newConceptsCount;
	private long inactivatedConceptsCount;
	private long reactivatedConceptsCount;
	private long changedFsnCount;
	private long inactivatedSynonymsCount;
	private long newSynonymsForExistingConceptsCount;
	private long reactivatedSynonymsCount;

	public void setNewConceptsCount(long newConceptsCount) {
		this.newConceptsCount = newConceptsCount;
	}

	public long getNewConceptsCount() {
		return newConceptsCount;
	}

	public void setInactivatedConceptsCount(long inactivatedConceptsCount) {
		this.inactivatedConceptsCount = inactivatedConceptsCount;
	}

	public long getInactivatedConceptsCount() {
		return inactivatedConceptsCount;
	}

	public void setReactivatedConceptsCount(long reactivatedConceptsCount) {
		this.reactivatedConceptsCount = reactivatedConceptsCount;
	}

	public long getReactivatedConceptsCount() {
		return reactivatedConceptsCount;
	}

	public void setChangedFsnCount(long changedFsnCount) {
		this.changedFsnCount = changedFsnCount;
	}

	public long getChangedFsnCount() {
		return changedFsnCount;
	}

	public void setInactivatedSynonymsCount(long inactivatedSynonymsCount) {
		this.inactivatedSynonymsCount = inactivatedSynonymsCount;
	}

	public long getInactivatedSynonymsCount() {
		return inactivatedSynonymsCount;
	}

	public void setNewSynonymsForExistingConceptsCount(long newSynonymsForExistingConceptsCount) {
		this.newSynonymsForExistingConceptsCount = newSynonymsForExistingConceptsCount;
	}

	public long getNewSynonymsForExistingConceptsCount() {
		return newSynonymsForExistingConceptsCount;
	}

	public void setReactivatedSynonymsCount(long reactivatedSynonymsCount) {
		this.reactivatedSynonymsCount = reactivatedSynonymsCount;
	}

	public long getReactivatedSynonymsCount() {
		return reactivatedSynonymsCount;
	}
}
