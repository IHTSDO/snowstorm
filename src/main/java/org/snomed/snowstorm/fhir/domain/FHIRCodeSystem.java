package org.snomed.snowstorm.fhir.domain;

public class FHIRCodeSystem {
	
	String name;
	String copyright;
	String version;
	
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getCopyright() {
		return copyright;
	}
	public void setCopyright(String copyright) {
		this.copyright = copyright;
	}
}
