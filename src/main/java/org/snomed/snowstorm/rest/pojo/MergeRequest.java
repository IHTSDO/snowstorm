package org.snomed.snowstorm.rest.pojo;

public class MergeRequest {

	private String source;
	private String target;
	private String commitComment;
	private String reviewId;

	public MergeRequest() {
	}

	public MergeRequest(String source, String target, String commitComment, String reviewId) {
		this.source = source;
		this.target = target;
		this.commitComment = commitComment;
		this.reviewId = reviewId;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public String getCommitComment() {
		return commitComment;
	}

	public void setCommitComment(String commitComment) {
		this.commitComment = commitComment;
	}

	public String getReviewId() {
		return reviewId;
	}

	public void setReviewId(String reviewId) {
		this.reviewId = reviewId;
	}
}
