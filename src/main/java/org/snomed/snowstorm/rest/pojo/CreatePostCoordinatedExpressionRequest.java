package org.snomed.snowstorm.rest.pojo;

public class CreatePostCoordinatedExpressionRequest {

	private String moduleId;
	private String closeToUserForm;

	public CreatePostCoordinatedExpressionRequest() {
	}

	public String getCloseToUserForm() {
		return closeToUserForm;
	}

	public String getModuleId() {
		return moduleId;
	}
}
