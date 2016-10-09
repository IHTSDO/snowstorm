package com.kaicube.snomed.elasticsnomed.rest.pojo;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class CreateReviewRequest {

	@NotNull
	@Min(4)
	private String source;

	@NotNull
	@Min(4)
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
