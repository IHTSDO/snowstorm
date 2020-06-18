package org.snomed.snowstorm.rest.pojo;

public class CreateCodeSystemVersionRequest {

	private Integer effectiveDate;
	private String description;

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

}
