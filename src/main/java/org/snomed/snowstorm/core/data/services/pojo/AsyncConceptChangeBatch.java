package org.snomed.snowstorm.core.data.services.pojo;

import java.util.List;
import java.util.UUID;

public class AsyncConceptChangeBatch {

	private String id;
	private Status status;
	private List<Long> conceptIds;
	private String message;

	public AsyncConceptChangeBatch() {
		id = UUID.randomUUID().toString();
		status = Status.RUNNING;
	}

	public String getId() {
		return id;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public List<Long> getConceptIds() {
		return conceptIds;
	}

	public void setConceptIds(List<Long> conceptIds) {
		this.conceptIds = conceptIds;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public enum Status {
		RUNNING, COMPLETED, FAILED
	}
}
