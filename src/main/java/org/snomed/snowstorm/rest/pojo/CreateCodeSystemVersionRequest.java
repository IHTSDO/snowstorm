package org.snomed.snowstorm.rest.pojo;

public class CreateCodeSystemVersionRequest {

	private String effectiveDate;
	private String description;

	public String getEffectiveDate() {
		return effectiveDate;
	}

	public void setEffectiveDate(String effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
