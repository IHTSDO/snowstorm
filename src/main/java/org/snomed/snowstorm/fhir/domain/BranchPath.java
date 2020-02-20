package org.snomed.snowstorm.fhir.domain;

/**
 * Wrapper for a branch path string to simulate passing by reference so as to set in a function
 */
public class BranchPath {

	String branchPathStr;
	
	public BranchPath() {}
	
	public BranchPath(String branchPath) {
		set(branchPath);
	}
	
	public void set(String branchPath) {
		this.branchPathStr = branchPath;
	}
	
	public void set(BranchPath branchPath) {
		set(branchPath.toString());
	}
	
	public String toString() {
		return branchPathStr;
	}
}
