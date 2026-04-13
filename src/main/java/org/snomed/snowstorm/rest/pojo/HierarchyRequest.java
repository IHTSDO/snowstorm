package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"codes", "includeTerms"})
public class HierarchyRequest {

	private List<String> codes;
	private boolean includeTerms;

	public HierarchyRequest() {
		codes = new ArrayList<>();
	}

	public List<String> getCodes() {
		return codes;
	}

	public void setCodes(List<String> codes) {
		if (codes != null) {
			codes.removeIf(Objects::isNull);
		}
		this.codes = codes != null ? codes : new ArrayList<>();
	}

	public boolean isIncludeTerms() {
		return includeTerms;
	}

	public void setIncludeTerms(boolean includeTerms) {
		this.includeTerms = includeTerms;
	}
}
