package org.snomed.snowstorm.rest.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

public class CreateCodeSystemVersionRequest {

	private Integer effectiveDate;
	private String description;

	@Schema(defaultValue = "false")
	private boolean internalRelease;

	public CreateCodeSystemVersionRequest() {
	}

	public CreateCodeSystemVersionRequest(Integer effectiveDate, String description) {
		this.effectiveDate = effectiveDate;
		this.description = description;
	}

	public Integer getEffectiveDate() {
		return effectiveDate;
	}

	public void setEffectiveDate(Integer effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isInternalRelease() {
		return internalRelease;
	}

	public void setInternalRelease(boolean internalRelease) {
		this.internalRelease = internalRelease;
	}
}
