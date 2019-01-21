package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.domain.DomainEntity;
import org.elasticsearch.common.Strings;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

public abstract class SnomedComponent<C> extends DomainEntity<C> implements IdAndEffectiveTimeComponent {

	public interface Fields {
		String EFFECTIVE_TIME = "effectiveTimeI";
		String ACTIVE = "active";
		String MODULE_ID = "moduleId";
	}

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Boolean)
	protected boolean active;

	@Field(type = FieldType.Integer)
	private Integer effectiveTimeI;

	@Field(type = FieldType.keyword)
	@JsonView(value = View.Component.class)
	private boolean released;

	@Field(type = FieldType.keyword)
	private String releaseHash;

	@Field(type = FieldType.keyword)
	@JsonView(value = View.Component.class)
	private Integer releasedEffectiveTime;

	@Transient
	@JsonIgnore
	private boolean creating;

	public abstract String getIdField();

	public void release(Integer effectiveTime) {
		setReleaseHash(buildReleaseHash());
		setEffectiveTimeI(effectiveTime);
		setReleasedEffectiveTime(effectiveTime);
		setReleased(true);
	}

	/**
	 * If component has been released and it's state is the same at last release
	 * then restore effectiveTime, otherwise clear effectiveTime.
	 */
	public void updateEffectiveTime() {
		if (isReleased() && getReleaseHash().equals(buildReleaseHash())) {
			setEffectiveTimeI(getReleasedEffectiveTime());
		} else {
			setEffectiveTimeI(null);
		}
	}

	public void copyReleaseDetails(SnomedComponent<C> component) {
		setEffectiveTimeI(component.getEffectiveTimeI());
		setReleased(component.isReleased());
		setReleaseHash(component.getReleaseHash());
		setReleasedEffectiveTime(component.getReleasedEffectiveTime());
	}

	public void clearReleaseDetails() {
		setEffectiveTimeI(null);
		setReleased(false);
		setReleaseHash(null);
		setReleasedEffectiveTime(null);
	}

	private String buildReleaseHash() {
		return Strings.arrayToDelimitedString(getReleaseHashObjects(), "|");
	}

	protected abstract Object[] getReleaseHashObjects();

	public boolean isActive() {
		return active;
	}

	public SnomedComponent<C> setActive(boolean active) {
		this.active = active;
		return this;
	}

	public boolean isReleased() {
		return released;
	}

	public void setReleased(boolean released) {
		this.released = released;
	}

	public String getReleaseHash() {
		return releaseHash;
	}

	public void setReleaseHash(String releaseHash) {
		this.releaseHash = releaseHash;
	}

	public Integer getReleasedEffectiveTime() {
		return releasedEffectiveTime;
	}

	public void setReleasedEffectiveTime(Integer releasedEffectiveTime) {
		this.releasedEffectiveTime = releasedEffectiveTime;
	}

	@JsonView(value = View.Component.class)
	public String getEffectiveTime() {
		return effectiveTimeI != null ? effectiveTimeI.toString() : null;
	}

	public Integer getEffectiveTimeI() {
		return effectiveTimeI;
	}

	public void setEffectiveTimeI(Integer effectiveTimeI) {
		this.effectiveTimeI = effectiveTimeI;
	}

	public void setCreating(boolean creating) {
		this.creating = creating;
	}

	public boolean isCreating() {
		return creating;
	}

}
