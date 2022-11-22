package org.snomed.snowstorm.rest.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import org.snomed.snowstorm.core.rf2.RF2Type;

import java.util.List;

public class ImportCreationRequest {

	private RF2Type type;

	@Schema(example = "MAIN", required = true)
	private String branchPath;

	@Schema(defaultValue = "false")
	private boolean createCodeSystemVersion;

	@Schema(defaultValue = "false")
	private boolean internalRelease;

	@Schema(defaultValue = "[]")
	List<String> filterModuleIds;

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

	public List<String> getFilterModuleIds() {
		return filterModuleIds;
	}

	public void setFilterModuleIds(List<String> filterModuleIds) {
		this.filterModuleIds = filterModuleIds;
	}
}
