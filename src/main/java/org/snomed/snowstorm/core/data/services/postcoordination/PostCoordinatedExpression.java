package org.snomed.snowstorm.core.data.services.postcoordination;

import java.util.Objects;

public class PostCoordinatedExpression {

	private String id;
	private String closeToUserForm;

	public PostCoordinatedExpression() {
	}

	public PostCoordinatedExpression(String id, String closeToUserForm) {
		this.id = id;
		this.closeToUserForm = closeToUserForm;
	}

	public String getId() {
		return id;
	}

	public String getCloseToUserForm() {
		return closeToUserForm;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PostCoordinatedExpression that = (PostCoordinatedExpression) o;
		return id.equals(that.id) &&
				closeToUserForm.equals(that.closeToUserForm);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, closeToUserForm);
	}
}
