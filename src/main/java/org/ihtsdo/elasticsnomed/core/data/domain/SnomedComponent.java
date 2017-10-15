package org.ihtsdo.elasticsnomed.core.data.domain;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.domain.DomainEntity;
import org.ihtsdo.elasticsnomed.rest.View;
import org.elasticsearch.common.Strings;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

public abstract class SnomedComponent<C> extends DomainEntity<C> {

	public interface Fields {
		String ACTIVE = "active";
		String EFFECTIVE_TIME = "effectiveTime";
	}

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	protected boolean active;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@JsonView(value = View.Component.class)
	private String effectiveTime;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@JsonView(value = View.Component.class)
	private boolean released;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String releaseHash;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@JsonView(value = View.Component.class)
	private String releasedEffectiveTime;

	@Transient
	private boolean creating;

	public void release(String effectiveTime) {
		setReleaseHash(buildReleaseHash());
		setEffectiveTime(effectiveTime);
		setReleasedEffectiveTime(effectiveTime);
		setReleased(true);
	}

	/**
	 * If component has been released and it's state is the same at last release
	 * then restore effectiveTime, otherwise clear effectiveTime.
	 */
	public void updateEffectiveTime() {
		if (isReleased() && getReleaseHash().equals(buildReleaseHash())) {
			setEffectiveTime(getReleasedEffectiveTime());
		} else {
			setEffectiveTime(null);
		}
	}

	public void copyReleaseDetails(SnomedComponent<C> component) {
		setEffectiveTime(component.getEffectiveTime());
		setReleased(component.isReleased());
		setReleaseHash(component.getReleaseHash());
		setReleasedEffectiveTime(component.getReleasedEffectiveTime());
	}

	public void clearReleaseDetails() {
		setEffectiveTime(null);
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

	public String getReleasedEffectiveTime() {
		return releasedEffectiveTime;
	}

	public void setReleasedEffectiveTime(String releasedEffectiveTime) {
		this.releasedEffectiveTime = releasedEffectiveTime;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	public void setCreating(boolean creating) {
		this.creating = creating;
	}

	public boolean isCreating() {
		return creating;
	}

}
