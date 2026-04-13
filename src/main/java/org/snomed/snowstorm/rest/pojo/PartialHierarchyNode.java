package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;

@JsonPropertyOrder({"code", "parents", "term"})
public class PartialHierarchyNode {

	private String code;
	private String[] parents;
	private String term;

	public PartialHierarchyNode() {
	}

	public PartialHierarchyNode(String code, String[] parents, String term) {
		this.code = code;
		this.parents = parents;
		this.term = term;
	}

	@JsonView(value = View.Component.class)
	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	@JsonView(value = View.Component.class)
	public String[] getParents() {
		return parents;
	}

	public void setParents(String[] parents) {
		this.parents = parents;
	}

	@JsonView(value = View.Component.class)
	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}
}
