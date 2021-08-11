package org.snomed.snowstorm.rest.pojo;

/**
 * Wrapper for receiving 'SetAuthorFlag' requests.
 */
public class SetAuthorFlag {
	private String name;
	private boolean value;

	public SetAuthorFlag() {

	}

	public SetAuthorFlag(String name, boolean value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isValue() {
		return value;
	}

	public void setValue(boolean value) {
		this.value = value;
	}
}
