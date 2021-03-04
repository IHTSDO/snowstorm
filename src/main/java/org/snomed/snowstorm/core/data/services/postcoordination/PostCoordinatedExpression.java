package org.snomed.snowstorm.core.data.services.postcoordination;

import java.util.Objects;

public class PostCoordinatedExpression {

	private String id;
	private String closeToUserForm;
	private String classifiableForm;
	private String humanReadableClassifiableForm;

	public PostCoordinatedExpression() {
	}

	public PostCoordinatedExpression(String id, String closeToUserForm, String classifiableForm) {
		this.id = id;
		this.closeToUserForm = closeToUserForm;
		this.classifiableForm = classifiableForm;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCloseToUserForm() {
		return closeToUserForm;
	}

	public String getClassifiableForm() {
		return classifiableForm;
	}

	public void setHumanReadableClassifiableForm(String humanReadableClassifiableForm) {
		this.humanReadableClassifiableForm = humanReadableClassifiableForm;
	}

	public String getHumanReadableClassifiableForm() {
		return humanReadableClassifiableForm;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PostCoordinatedExpression that = (PostCoordinatedExpression) o;
		return id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
