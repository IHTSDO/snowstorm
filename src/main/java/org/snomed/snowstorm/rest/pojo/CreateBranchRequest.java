package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

public class CreateBranchRequest {

	private String parent;
	private String name;
	private Map<String, Object> metadata;

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

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	@JsonIgnore
	public String getBranch() {
		return getParent() + "/" + getName();
	}
}
