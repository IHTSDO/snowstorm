package com.kaicube.snomed.elasticsnomed.rest.pojo;

import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;

public class CreateReviewRequest {

	@NotNull
	@Length(min = 4)
	private String source;

	@NotNull
	@Length(min = 4)
	private String target;

	public CreateReviewRequest() {
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
}
