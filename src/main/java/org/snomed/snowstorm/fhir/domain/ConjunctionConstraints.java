package org.snomed.snowstorm.fhir.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConjunctionConstraints {

	private final List<DisjunctionConstraints> disjunctionConstraints = new ArrayList<>();

	public static class DisjunctionConstraints {
		Set<ConceptConstraint> constraints = new HashSet<>();

		public DisjunctionConstraints(Set<ConceptConstraint> constraints) {
			this.constraints.addAll(constraints);
		}

		public Set<ConceptConstraint> getConstraints() {
			return constraints;
		}
	}

	public void addDisjunctionConstraints(Set<ConceptConstraint> constraints) {
		disjunctionConstraints.add(new DisjunctionConstraints(constraints));
	}

	public List<DisjunctionConstraints> getDisjunctionConstraints() {
		return disjunctionConstraints;
	}

	public Set<ConceptConstraint> constraintsFlattened() {
		return disjunctionConstraints.stream().flatMap(disjunction -> disjunction.getConstraints().stream()).collect(Collectors.toSet());
	}

	public boolean isEmpty() {
		return disjunctionConstraints.isEmpty();
	}
}
