package org.snomed.snowstorm.fhir.domain.element;

public class FHIRCoding {
	
	String system;
	String version;
	String code;
	String display;
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
