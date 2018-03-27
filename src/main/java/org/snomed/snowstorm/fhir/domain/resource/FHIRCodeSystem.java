package org.snomed.snowstorm.fhir.domain.resource;

public class FHIRCodeSystem extends FHIRResource {
	
	String name;
	String copyright;
	String version;
	String publisher;
	Boolean experimental = false;
	
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
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getPublisher() {
		return publisher;
	}
	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}
}
