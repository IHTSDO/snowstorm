package org.ihtsdo.elasticsnomed.rest.pojo;

public class CreateBranchRequest {

	private String parent;
	private String name;

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
}
