package org.snomed.snowstorm.fhir.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AndConstraints {

	private final List<OrConstraints> andConstraints = new ArrayList<>();

	public static class OrConstraints {
		Set<ConceptConstraint> orConstraints = new HashSet<>();

		public OrConstraints(Set<ConceptConstraint> orConstraints) {
			this.orConstraints.addAll(orConstraints);
		}

		public Set<ConceptConstraint> getOrConstraints() {
			return orConstraints;
		}
	}

	public void addOrConstraints(Set<ConceptConstraint> orConstraints) {
		OrConstraints orConstraintsSet = new OrConstraints(orConstraints);
		andConstraints.add(orConstraintsSet);
	}

	public List<OrConstraints> getAndConstraints() {
		return andConstraints;
	}

	public Set<ConceptConstraint> constraintsFlattened() {
		return andConstraints.stream().flatMap(orConstraints-> orConstraints.getOrConstraints().stream()).collect(Collectors.toSet());
	}

	public boolean isEmpty() {
		return andConstraints.isEmpty();
	}
}
