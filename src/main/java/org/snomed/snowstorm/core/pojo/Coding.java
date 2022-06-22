package org.snomed.snowstorm.core.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;

/**
 * Class to represent a concept in a non-snomed code system.
 */
public class Coding {

	private String system;
	private String code;
	private String display;

	public Coding() {
	}

	public Coding(String system, String code, String display) {
		this.system = system;
		this.code = code;
		this.display = display;
	}

	@JsonView(value = View.Component.class)
	public String getSystem() {
		return system;
	}

	@JsonView(value = View.Component.class)
	public String getCode() {
		return code;
	}

	@JsonView(value = View.Component.class)
	public String getDisplay() {
		return display;
	}

	@Override
	public String toString() {
		return "Coding{" +
				"system='" + system + '\'' +
				", code='" + code + '\'' +
				", display='" + display + '\'' +
				'}';
	}
}
