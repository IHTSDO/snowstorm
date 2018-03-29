package org.snomed.snowstorm.fhir.domain.element;

import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.rest.View;

import com.fasterxml.jackson.annotation.JsonView;

public class FHIRCoding implements FHIRConstants {
	
	@JsonView(value = View.Component.class)
	String system = SNOMED_URI;
	
	@JsonView(value = View.Component.class)
	String version;
	
	@JsonView(value = View.Component.class)
	String code;
	
	@JsonView(value = View.Component.class)
	String display;
	
	@JsonView(value = View.Component.class)
	Boolean userSelected;
	
	
	public String getSystem() {
		return system;
	}
	public void setSystem(String system) {
		this.system = system;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getDisplay() {
		return display;
	}
	public void setDisplay(String display) {
		this.display = display;
	}
	public Boolean getUserSelected() {
		return userSelected;
	}
	public void setUserSelected(Boolean userSelected) {
		this.userSelected = userSelected;
	}

}
