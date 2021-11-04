package org.snomed.snowstorm.rest.pojo;

public class CodeSystemNewAuthoringCycleRequest {

	private String previousPackage;
	private String previousRelease;
	private String previousDependency;
	
	public CodeSystemNewAuthoringCycleRequest() {
	
	}
	
	public CodeSystemNewAuthoringCycleRequest(String previousPackage, String previousRelease) {
		this.previousPackage = previousPackage;
		this.previousRelease = previousRelease;
	}
	
	public CodeSystemNewAuthoringCycleRequest(String previousPackage, String previousRelease, String previousDependency) {
		this.previousPackage = previousPackage;
		this.previousRelease = previousRelease;
		this.previousDependency = previousDependency;
	}
	
	public String getPreviousRelease() {
		return previousRelease;
	}
	
	public void setPreviousRelease(String previousRelease) {
		this.previousRelease = previousRelease;
	}
	
	public String getPreviousPackage() {
		return previousPackage;
	}
	
	public void setPreviousPackage(String previousPackage) {
		this.previousPackage = previousPackage;
	}
	
	public String getPreviousDependency() {
		return previousDependency;
	}
	
	public void setPreviousDependency(String previousDependency) {
		this.previousDependency = previousDependency;
	}

}
