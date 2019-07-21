package org.snomed.snowstorm.core.data.domain.review;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;

@Document(indexName = "branch-merge-review")
public class MergeReview {

	@Id
	private String id;
	private String sourcePath;
	private String targetPath;
	private String sourceToTargetReviewId;
	private String targetToSourceReviewId;

	private ReviewStatus status;
	private String message;
	private Date created;// To find and delete old docs

	public MergeReview() {
	}

	public MergeReview(String id, String sourcePath, String targetPath, String sourceToTargetReviewId, String targetToSourceReviewId) {
		this.id = id;
		this.sourcePath = sourcePath;
		this.targetPath = targetPath;
		this.sourceToTargetReviewId = sourceToTargetReviewId;
		this.targetToSourceReviewId = targetToSourceReviewId;
		this.created = new Date();
	}

	public ReviewStatus getStatus() {
		return status;
	}

	public void setStatus(ReviewStatus status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getId() {
		return id;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public String getTargetPath() {
		return targetPath;
	}

	public String getSourceToTargetReviewId() {
		return sourceToTargetReviewId;
	}

	public String getTargetToSourceReviewId() {
		return targetToSourceReviewId;
	}

	public Date getCreated() {
		return created;
	}
}
