package org.snomed.snowstorm.rest.pojo;

import org.snomed.snowstorm.core.rf2.RF2Type;

public class ImportCreationRequest {

	private RF2Type type;
	private String branchPath;

	private boolean createCodeSystemVersion;

	public RF2Type getType() {
		return type;
	}

	public void setType(RF2Type type) {
		this.type = type;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public boolean getCreateCodeSystemVersion() {
		return createCodeSystemVersion;
	}

	public void setCreateCodeSystemVersion(boolean createCodeSystemVersion) {
		this.createCodeSystemVersion = createCodeSystemVersion;
	}
}
