package org.snomed.snowstorm.core.data.services.postcoordination.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierHelper;

import java.util.Objects;

public class PostCoordinatedExpression {

	private String id;
	private String closeToUserForm;
	private String humanReadableCloseToUserForm;
	private String classifiableForm;
	private String humanReadableClassifiableForm;
	private ComparableExpression necessaryNormalForm;
	private String humanReadableNecessaryNormalForm;
	private ServiceException exception;

	public PostCoordinatedExpression() {
	}

	public PostCoordinatedExpression(String closeToUserForm, ServiceException exception) {
		this.closeToUserForm = closeToUserForm;
		this.exception = exception;
	}

	public PostCoordinatedExpression(String id, String closeToUserForm, String classifiableForm, ComparableExpression necessaryNormalForm) {
		this.id = id;
		this.closeToUserForm = closeToUserForm;
		this.classifiableForm = classifiableForm;
		this.necessaryNormalForm = necessaryNormalForm;
	}

	public boolean hasException() {
		return exception != null;
	}

	public boolean hasNullId() {
		return id == null;
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

	public void setCloseToUserForm(String closeToUserForm) {
		this.closeToUserForm = closeToUserForm;
	}

	public ServiceException getException() {
		return exception;
	}

	public String getClassifiableForm() {
		return classifiableForm;
	}

	public String getNecessaryNormalForm() {
		return necessaryNormalForm != null ? necessaryNormalForm.toString() : null;
	}

	@JsonIgnore
	public ComparableExpression getNecessaryNormalFormExpression() {
		return necessaryNormalForm;
	}

	public void setNecessaryNormalForm(ComparableExpression necessaryNormalForm) {
		this.necessaryNormalForm = necessaryNormalForm;
	}

	public String getHumanReadableCloseToUserForm() {
		return humanReadableCloseToUserForm;
	}

	public void setHumanReadableCloseToUserForm(String humanReadableCloseToUserForm) {
		this.humanReadableCloseToUserForm = humanReadableCloseToUserForm;
	}

	public String getHumanReadableCloseToUserFormWithoutIds() {
		return humanReadableCloseToUserForm == null ? null : humanReadableCloseToUserForm.replaceAll(IdentifierHelper.SCTID_PATTERN + " ?", "");
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

	public void setException(ServiceException exception) {
		this.exception = exception;
		classifiableForm = null;
		necessaryNormalForm = null;
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
