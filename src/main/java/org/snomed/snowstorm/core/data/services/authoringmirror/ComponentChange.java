package org.snomed.snowstorm.core.data.services.authoringmirror;

public class ComponentChange {

	private String componentType;
	private String componentId;
	private String type;

	public ComponentChange() {
	}

	public String getComponentType() {
		return componentType;
	}

	public void setComponentType(String componentType) {
		this.componentType = componentType;
	}

	public String getComponentId() {
		return componentId;
	}

	public void setComponentId(String componentId) {
		this.componentId = componentId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
