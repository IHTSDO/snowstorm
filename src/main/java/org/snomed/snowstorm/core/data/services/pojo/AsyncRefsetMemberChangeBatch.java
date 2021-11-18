package org.snomed.snowstorm.core.data.services.pojo;

import org.snomed.snowstorm.core.util.TimerUtil;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class AsyncRefsetMemberChangeBatch {

	private final String id;
	private final Date startTime;
	private Status status;
	private List<String> memberIds;
	private Date endTime;
	private String message;
	private Float secondsDuration;

	public AsyncRefsetMemberChangeBatch() {
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
			secondsDuration = TimerUtil.getDuration(startTime.getTime(), endTime.getTime());
		}
	}

	public List<String> getMemberIds() {
		return memberIds;
	}

	public void setMemberIds(List<String> memberIds) {
		this.memberIds = memberIds;
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

	public Float getSecondsDuration() {
		return secondsDuration;
	}

	public enum Status {
		RUNNING, COMPLETED, FAILED
	}
}
