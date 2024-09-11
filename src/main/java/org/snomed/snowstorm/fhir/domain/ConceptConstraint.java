package org.snomed.snowstorm.fhir.domain;

import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public class ConceptConstraint {

	private Collection<String> code;
	private Set<String> parent;// ECL used instead for SNOMED
	private Set<String> ancestor;
	private Boolean activeOnly;
	private String ecl;

	public ConceptConstraint setActiveOnly(Boolean activeOnly) {
		this.activeOnly = activeOnly;
		return this;
	}

	public ConceptConstraint() {
	}

	public ConceptConstraint(Collection<String> code) {
		this.code = code;
	}

	public boolean isSimpleCodeSet() {
		return CollectionUtils.isEmpty(parent) && CollectionUtils.isEmpty(ancestor) && ecl == null && !CollectionUtils.isEmpty(code);
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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ConceptConstraint that = (ConceptConstraint) o;
		return Objects.equals(code, that.code) && Objects.equals(parent, that.parent) && Objects.equals(ancestor, that.ancestor) && Objects.equals(ecl, that.ecl);
	}

	@Override
	public int hashCode() {
		return Objects.hash(code, parent, ancestor, ecl);
	}
}
