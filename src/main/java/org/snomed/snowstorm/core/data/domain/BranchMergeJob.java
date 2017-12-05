package org.snomed.snowstorm.core.data.domain;

import java.util.Date;
import java.util.UUID;

public class BranchMergeJob {

	private final String id;
	private final String source;
	private final String target;
	private final Date scheduledDate;
	private Date startDate;
	private JobStatus status;
	private Date endDate;

	public BranchMergeJob(String source, String target, JobStatus status) {
		id = UUID.randomUUID().toString();
		this.source = source;
		this.target = target;
		scheduledDate = new Date();
		this.status = status;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public void setStatus(JobStatus status) {
		this.status = status;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public String getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public String getTarget() {
		return target;
	}

	public Date getStartDate() {
		return startDate;
	}

	public Date getScheduledDate() {
		return scheduledDate;
	}

	public JobStatus getStatus() {
		return status;
	}

	public Date getEndDate() {
		return endDate;
	}
}
