package org.snomed.snowstorm.fhir.domain;

import java.util.Collection;
import java.util.Set;

public class ConceptConstraint {

	private Collection<String> code;
	private Set<String> parent;
	private Set<String> ancestor;
	private String ecl;

	public ConceptConstraint() {
	}

	public ConceptConstraint(Collection<String> code) {
		this.code = code;
	}

	public ConceptConstraint setParent(Set<String> parent) {
		this.parent = parent;
		return this;
	}

	public ConceptConstraint setAncestor(Set<String> ancestor) {
		this.ancestor = ancestor;
		return this;
	}

	public ConceptConstraint setEcl(String ecl) {
		this.ecl = ecl;
		return this;
	}

	public boolean hasEcl() {
		return ecl != null;
	}

	public Collection<String> getCode() {
		return code;
	}

	public Set<String> getParent() {
		return parent;
	}

	public Set<String> getAncestor() {
		return ancestor;
	}

	public String getEcl() {
		return ecl;
	}
}
