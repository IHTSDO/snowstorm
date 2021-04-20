package org.snomed.snowstorm.core.data.services.postcoordination.model;

import java.util.Objects;

public class PostCoordinatedExpression {

	private String id;
	private String closeToUserForm;
	private String classifiableForm;
	private String humanReadableClassifiableForm;
	private String necessaryNormalForm;
	private String humanReadableNecessaryNormalForm;

	public PostCoordinatedExpression() {
	}

	public PostCoordinatedExpression(String id, String closeToUserForm, String classifiableForm, String necessaryNormalForm) {
		this.id = id;
		this.closeToUserForm = closeToUserForm;
		this.classifiableForm = classifiableForm;
		this.necessaryNormalForm = necessaryNormalForm;
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

	public String getNecessaryNormalForm() {
		return necessaryNormalForm;
	}

	public void setHumanReadableClassifiableForm(String humanReadableClassifiableForm) {
		this.humanReadableClassifiableForm = humanReadableClassifiableForm;
	}

	public String getHumanReadableClassifiableForm() {
		return humanReadableClassifiableForm;
	}

	public String getHumanReadableNecessaryNormalForm() {
		return humanReadableNecessaryNormalForm;
	}

	public void setHumanReadableNecessaryNormalForm(String humanReadableNecessaryNormalForm) {
		this.humanReadableNecessaryNormalForm = humanReadableNecessaryNormalForm;
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
