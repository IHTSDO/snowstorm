package org.snomed.snowstorm.rest.pojo;

import jakarta.validation.constraints.NotNull;

public class BranchLabelledEventRequest {
	public enum EventType {
		DELETE
	}

	@NotNull
	private EventType event;

	public EventType getEvent() {
		return event;
	}

	public void setEvent(EventType event) {
		this.event = event;
	}
}
