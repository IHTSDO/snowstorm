package org.snomed.snowstorm.fhir.domain;

import org.springframework.util.CollectionUtils;

import java.util.*;

import static org.snomed.snowstorm.fhir.domain.ConceptConstraint.Type.TERMS;

public class ConceptConstraint {

	public enum Type{
		TERMS,
		REGEX
	}
	private Collection<String> code;
	private Set<String> parent;// ECL used instead for SNOMED
	private Set<String> ancestor;
	private Boolean activeOnly;
	private String ecl;
	private Map<String,Collection<String>> properties;
	private Type type = TERMS;

	public Boolean isActiveOnly() {
		return activeOnly;
	}

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
		return CollectionUtils.isEmpty(parent) && CollectionUtils.isEmpty(ancestor) && ecl == null && !CollectionUtils.isEmpty(code) && getType()==TERMS;
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


	public Map<String, Collection<String>> getProperties() {
		return properties;
	}

	public ConceptConstraint setProperties(Map<String, Collection<String>> properties) {
		this.properties = properties;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ConceptConstraint that = (ConceptConstraint) o;
		return Objects.equals(code, that.code) && Objects.equals(parent, that.parent) && Objects.equals(ancestor, that.ancestor) && Objects.equals(ecl, that.ecl) && Objects.equals(properties, that.properties) && type == that.type && Objects.equals(activeOnly, that.activeOnly);
	}

	@Override
	public int hashCode() {
		return Objects.hash(code, parent, ancestor, ecl, properties, type, activeOnly);
	}

	public Type getType() {
		return type;
	}

	public ConceptConstraint setType(Type type) {
		this.type = type;
		return this;
	}


}
