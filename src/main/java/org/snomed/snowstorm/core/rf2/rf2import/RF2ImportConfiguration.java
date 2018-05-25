package org.snomed.snowstorm.core.rf2.rf2import;

import org.snomed.snowstorm.core.rf2.RF2Type;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class RF2ImportConfiguration {

	private RF2Type type;
	private String branchPath;
	private Set<String> moduleIds;

	public RF2ImportConfiguration() {
	}

	public RF2ImportConfiguration(RF2Type type, String branchPath) {
		this.type = type;
		this.branchPath = branchPath;
		moduleIds = new HashSet<>();
	}

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

	public Set<String> getModuleIds() {
		return moduleIds != null ? moduleIds : Collections.EMPTY_SET;
	}

	public void setModuleIds(Set<String> moduleIds) {
		this.moduleIds = moduleIds;
	}
}
