package org.snomed.snowstorm.core.data.services.postcoordination;

import java.util.Objects;

public class PostCoordinatedExpression {

	private String closeToUserForm;

	public PostCoordinatedExpression() {
	}

	public PostCoordinatedExpression(String closeToUserForm) {
		this.closeToUserForm = closeToUserForm;
	}

	public String getCloseToUserForm() {
		return closeToUserForm;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PostCoordinatedExpression that = (PostCoordinatedExpression) o;
		return Objects.equals(closeToUserForm, that.closeToUserForm);
	}

	@Override
	public int hashCode() {
		return Objects.hash(closeToUserForm);
	}
}
