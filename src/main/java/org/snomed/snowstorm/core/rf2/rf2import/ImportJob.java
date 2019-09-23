package org.snomed.snowstorm.core.rf2.rf2import;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.snowstorm.core.rf2.RF2Type;

import java.util.Set;

public class ImportJob {

	private RF2ImportConfiguration importConfiguration;

	public ImportJob(RF2ImportConfiguration importConfiguration) {
		this.importConfiguration = importConfiguration;
		status = ImportStatus.WAITING_FOR_FILE;
	}

	public enum ImportStatus {
		WAITING_FOR_FILE, RUNNING, COMPLETED, FAILED;
	}

	private ImportStatus status;

	private String errorMessage;

	public void setStatus(ImportStatus status) {
		this.status = status;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public RF2Type getType() {
		return importConfiguration.getType();
	}

	public String getBranchPath() {
		return importConfiguration.getBranchPath();
	}

	public Set<String> getModuleIds() {
		return importConfiguration.getModuleIds();
	}

	public Integer getPatchReleaseVersion() {
		return importConfiguration.getPatchReleaseVersion();
	}

	public boolean isCreateCodeSystemVersion() {
		return importConfiguration.isCreateCodeSystemVersion();
	}

	@JsonIgnore
	public boolean isClearEffectiveTimes() {
		return importConfiguration.isClearEffectiveTimes();
	}

	public ImportStatus getStatus() {
		return status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
