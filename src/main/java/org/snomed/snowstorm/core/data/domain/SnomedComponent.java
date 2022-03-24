package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.domain.DomainEntity;
import org.elasticsearch.common.Strings;
import org.slf4j.Logger;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.constraints.Size;
import java.io.Serializable;

public abstract class SnomedComponent<C> extends DomainEntity<C> implements IdAndEffectiveTimeComponent, Serializable {

	public interface Fields {
		String EFFECTIVE_TIME = "effectiveTimeI";
		String ACTIVE = "active";
		String MODULE_ID = "moduleId";
		String RELEASED = "released";
		String RELEASE_HASH = "releaseHash";
		String RELEASED_EFFECTIVE_TIME = "releasedEffectiveTime";
		String PATH = "path";
		String END = "end";
	}

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Boolean)
	protected boolean active;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Keyword)
	@Size(min = 5, max = 18)
	private String moduleId;

	@Field(type = FieldType.Integer)
	private Integer effectiveTimeI;

	@Field(type = FieldType.Keyword)
	@JsonView(value = View.Component.class)
	private boolean released;

	@Field(type = FieldType.Keyword)
	private String releaseHash;

	@Field(type = FieldType.Keyword)
	@JsonView(value = View.Component.class)
	private Integer releasedEffectiveTime;

	@Transient
	@JsonIgnore
	private boolean creating;

	@JsonIgnore
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

	public void updateEffectiveTime(Logger logger) {
		logger.info("Is released: {}", isReleased());
		logger.info("Get release hash: {}", getReleaseHash());
		logger.info("Build release hash: {}", buildReleaseHash());
		if (isReleased() && getReleaseHash().equals(buildReleaseHash())) {
			logger.info("Set effective time to releasedEffectiveTime: {}", getReleasedEffectiveTime());
			setEffectiveTimeI(getReleasedEffectiveTime());
		} else {
			logger.info("Set effective time to null");
			setEffectiveTimeI(null);
		}
	}

	public void copyReleaseDetails(SnomedComponent<C> component) {
		setEffectiveTimeI(component.getEffectiveTimeI());
		setReleased(component.isReleased());
		setReleaseHash(component.getReleaseHash());
		setReleasedEffectiveTime(component.getReleasedEffectiveTime());
	}

	public boolean isReleasedMoreRecentlyThan(SnomedComponent another) {
		if (another == null) {
			return this.isReleased();
		}
		if (this.isReleased() && another.isReleased() && (this.getReleasedEffectiveTime() > another.getReleasedEffectiveTime())) {
			return true;
		}
		if (this.isReleased() && !another.isReleased()) {
			return true;
		}
		return false;
	}

	public void copyReleaseDetails(SnomedComponent<C> existingComponent, SnomedComponent<C> existingParentComponent) {
		// Copy release details from the existing component, or the rebase parent branch version, whichever has been versioned more recently
		SnomedComponent<C> mostRecentlyPublished = existingParentComponent;
		if (existingComponent != null && existingComponent.isReleasedMoreRecentlyThan(mostRecentlyPublished)) {
			mostRecentlyPublished = existingComponent;
		}
		if (mostRecentlyPublished != null) {
			setEffectiveTimeI(mostRecentlyPublished.getEffectiveTimeI());
			setReleased(mostRecentlyPublished.isReleased());
			setReleaseHash(mostRecentlyPublished.getReleaseHash());
			setReleasedEffectiveTime(mostRecentlyPublished.getReleasedEffectiveTime());
		} else {
			clearReleaseDetails();
		}
	}

	public void clearReleaseDetails() {
		setEffectiveTimeI(null);
		setReleased(false);
		setReleaseHash(null);
		setReleasedEffectiveTime(null);
	}

	public String buildReleaseHash() {
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

	public String getModuleId() {
		return moduleId;
	}

	@SuppressWarnings("unchecked")
	public C setModuleId(String moduleId) {
		this.moduleId = moduleId;
		return (C)this;
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
