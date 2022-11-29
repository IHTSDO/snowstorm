package org.snomed.snowstorm.core.data.services.pojo;

public class CodeSystemUpgradeJob {

	public enum UpgradeStatus {
		RUNNING, COMPLETED, FAILED;
	}

	private Integer newDependantVersion;

	private String codeSystemShortname;

	private UpgradeStatus status;

	private String errorMessage;

	public CodeSystemUpgradeJob(String codeSystemShortname, Integer newDependantVersion) {
		this.newDependantVersion = newDependantVersion;
		this.codeSystemShortname = codeSystemShortname;
		this.status = UpgradeStatus.RUNNING;
	}

	public Integer getNewDependantVersion() {
		return newDependantVersion;
	}

	public String getCodeSystemShortname() {
		return codeSystemShortname;
	}

	public UpgradeStatus getStatus() {
		return status;
	}

	public void setStatus(UpgradeStatus status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
