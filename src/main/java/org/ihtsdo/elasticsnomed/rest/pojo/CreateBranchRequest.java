package org.ihtsdo.elasticsnomed.rest.pojo;

import java.util.Map;

public class CreateBranchRequest {

	private String parent;
	private String name;
	private Map<String, String> metadata;

	public String getParent() {
		return parent;
	}

	public void setParent(String parent) {
		this.parent = parent;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBranchPath() {
		return getParent() + "/" + getName();
	}

	public Map<String, String> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}
}
