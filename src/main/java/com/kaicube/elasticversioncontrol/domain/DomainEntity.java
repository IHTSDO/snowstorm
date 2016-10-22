package com.kaicube.elasticversioncontrol.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class DomainEntity<C> extends Entity {

	@JsonIgnore
	private boolean changed;

	public abstract String getId();

	public abstract boolean isComponentChanged(C existingComponent);

	public void setChanged(boolean changed) {
		this.changed = changed;
	}

	public void markChanged() {
		setChanged(true);
	}

	public boolean isChanged() {
		return changed;
	}
}
