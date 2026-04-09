package org.snomed.snowstorm.syndication;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.context.SecurityContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class InstallationTask {

	public enum InstallationStatus {
		PENDING,
		IN_PROGRESS,
		COMPLETED,
		FAILED
	}

	private String taskId;
	private String editionId;
	private String version;
	private InstallationStatus status;
	private String errorMessage;
	private Date createdAt;
	private Date completedAt;
	@JsonIgnore
	private List<String> downloadedFiles;
	@JsonIgnore
	private List<String> importJobIds;
	@JsonIgnore
	private SecurityContext securityContext;
	@JsonIgnore
	private List<String> derivativeContentItemVersions;

	public InstallationTask(String editionId, String version, List<String> derivativeContentItemVersions, SecurityContext securityContext) {
		this.taskId = UUID.randomUUID().toString();
		this.editionId = editionId;
		this.version = version;
		this.status = InstallationStatus.PENDING;
		this.createdAt = new Date();
		this.downloadedFiles = new ArrayList<>();
		this.importJobIds = new ArrayList<>();
		this.securityContext = securityContext;
		this.derivativeContentItemVersions = derivativeContentItemVersions == null ? Collections.emptyList()
				: new ArrayList<>(derivativeContentItemVersions);
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getEditionId() {
		return editionId;
	}

	public void setEditionId(String editionId) {
		this.editionId = editionId;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public InstallationStatus getStatus() {
		return status;
	}

	public void setStatus(InstallationStatus status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public Date getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Date completedAt) {
		this.completedAt = completedAt;
	}

	public List<String> getDownloadedFiles() {
		return downloadedFiles;
	}

	public void setDownloadedFiles(List<String> downloadedFiles) {
		this.downloadedFiles = downloadedFiles;
	}

	public List<String> getImportJobIds() {
		return importJobIds;
	}

	public void setImportJobIds(List<String> importJobIds) {
		this.importJobIds = importJobIds;
	}

	public SecurityContext getSecurityContext() {
		return securityContext;
	}

	public void setSecurityContext(SecurityContext securityContext) {
		this.securityContext = securityContext;
	}

	public List<String> getDerivativeContentItemVersions() {
		return derivativeContentItemVersions;
	}

	public void setDerivativeContentItemVersions(List<String> derivativeContentItemVersions) {
		this.derivativeContentItemVersions = derivativeContentItemVersions;
	}
}

