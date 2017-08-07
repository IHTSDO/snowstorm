package org.ihtsdo.elasticsnomed.core.rf2.rf2import;

public class ImportJob {

	public enum ImportStatus {
		WAITING_FOR_FILE, RUNNING, COMPLETED, FAILED;
	}

	private final ImportType type;
	private final String branchPath;
	private ImportStatus status;

	private String errorMessage;

	public ImportJob(ImportType type, String branchPath) {
		this.type = type;
		this.branchPath = branchPath;
		status = ImportStatus.WAITING_FOR_FILE;
	}

	public void setStatus(ImportStatus status) {
		this.status = status;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public ImportType getType() {
		return type;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public ImportStatus getStatus() {
		return status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
