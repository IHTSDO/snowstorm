package org.snomed.snowstorm.rest.pojo;

public class ImportPatchCreationRequest {

	private String branchPath;
	private Integer patchReleaseVersion;

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public Integer getPatchReleaseVersion() {
		return patchReleaseVersion;
	}

	public void setPatchReleaseVersion(Integer patchReleaseVersion) {
		this.patchReleaseVersion = patchReleaseVersion;
	}
}
