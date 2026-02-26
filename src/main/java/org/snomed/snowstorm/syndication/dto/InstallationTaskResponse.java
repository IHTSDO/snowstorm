package org.snomed.snowstorm.syndication.dto;

import org.snomed.snowstorm.syndication.InstallationTask;

import java.util.Date;
import java.util.List;

public class InstallationTaskResponse {

	private String taskId;
	private String editionId;
	private String version;
	private InstallationTask.InstallationStatus status;
	private String errorMessage;
	private Date createdAt;
	private Date completedAt;
	private List<String> importJobIds;

	public InstallationTaskResponse() {
	}

	public InstallationTaskResponse(InstallationTask task) {
		this.taskId = task.getTaskId();
		this.editionId = task.getEditionId();
		this.version = task.getVersion();
		this.status = task.getStatus();
		this.errorMessage = task.getErrorMessage();
		this.createdAt = task.getCreatedAt();
		this.completedAt = task.getCompletedAt();
		this.importJobIds = task.getImportJobIds();
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

	public InstallationTask.InstallationStatus getStatus() {
		return status;
	}

	public void setStatus(InstallationTask.InstallationStatus status) {
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

	public List<String> getImportJobIds() {
		return importJobIds;
	}

	public void setImportJobIds(List<String> importJobIds) {
		this.importJobIds = importJobIds;
	}
}

