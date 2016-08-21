package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class Component<C> extends Entity {

	@JsonIgnore
	private boolean changed;

	public abstract String getId();

	public abstract boolean isComponentChanged(C existingComponent);

	public void setChanged(boolean changed) {
		this.changed = changed;
	}

	public boolean isChanged() {
		return changed;
	}
}
