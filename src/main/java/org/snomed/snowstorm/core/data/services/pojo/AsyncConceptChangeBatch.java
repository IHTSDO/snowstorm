package org.snomed.snowstorm.core.data.services.pojo;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class AsyncConceptChangeBatch {

	private String id;
	private Status status;
	private List<Long> conceptIds;
	private String message;
	private Date startTime;
	private Date endTime;
	private Long secondsDuration;

	public AsyncConceptChangeBatch() {
		id = UUID.randomUUID().toString();
		status = Status.RUNNING;
		startTime = new Date();
	}

	public String getId() {
		return id;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
		if (status == Status.COMPLETED || status == Status.FAILED) {
			endTime = new Date();
			secondsDuration = startTime.getTime() - endTime.getTime();
		}
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

	public Date getStartTime() {
		return startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public Long getSecondsDuration() {
		return secondsDuration;
	}

	public enum Status {
		RUNNING, COMPLETED, FAILED
	}
}
