package org.snomed.snowstorm.rest.pojo;

import io.swagger.annotations.ApiModelProperty;
import org.snomed.snowstorm.core.rf2.RF2Type;

public class ImportCreationRequest {

	private RF2Type type;

	@ApiModelProperty(example = "MAIN", required = true)
	private String branchPath;

	@ApiModelProperty(value = "false")
	private boolean createCodeSystemVersion;

	@ApiModelProperty(value = "false")
	private boolean internalRelease;

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

	public boolean isInternalRelease() {
		return internalRelease;
	}

	public void setInternalRelease(boolean internalRelease) {
		this.internalRelease = internalRelease;
	}
}
