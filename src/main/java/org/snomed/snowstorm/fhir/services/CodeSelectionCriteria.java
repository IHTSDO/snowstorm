package org.snomed.snowstorm.fhir.services;

import org.snomed.snowstorm.fhir.domain.AndConstraints;
import org.snomed.snowstorm.fhir.domain.ConceptConstraint;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.springframework.util.CollectionUtils;

import java.util.*;

public class CodeSelectionCriteria {

	private final String valueSetUserRef;
	private final Map<FHIRCodeSystemVersion, AndConstraints> inclusionConstraints;
	private final Set<CodeSelectionCriteria> nestedSelections;
	private final Map<FHIRCodeSystemVersion, AndConstraints> exclusionConstraints;

	public CodeSelectionCriteria(String valueSetUserRef) {
		this.valueSetUserRef = valueSetUserRef;
		inclusionConstraints = new HashMap<>();
		nestedSelections = new HashSet<>();
		exclusionConstraints = new HashMap<>();
	}

	public boolean isOnlyInclusionsForOneVersionAndAllSimple() {
		return CollectionUtils.isEmpty(nestedSelections) && CollectionUtils.isEmpty(exclusionConstraints) && !CollectionUtils.isEmpty(inclusionConstraints)
				&& inclusionConstraints.keySet().size() == 1 && inclusionConstraints.values().stream().flatMap(andConstraints -> andConstraints.constraintsFlattened().stream()).allMatch(ConceptConstraint::isSimpleCodeSet);
	}

	public AndConstraints addInclusion(FHIRCodeSystemVersion codeSystemVersion) {
		return inclusionConstraints.computeIfAbsent(codeSystemVersion, v -> new AndConstraints());
	}

	public void addNested(CodeSelectionCriteria nestedCriteria) {
		nestedSelections.add(nestedCriteria);
	}

	public AndConstraints addExclusion(FHIRCodeSystemVersion codeSystemVersion) {
		return exclusionConstraints.computeIfAbsent(codeSystemVersion, v -> new AndConstraints());
	}

	public Set<FHIRCodeSystemVersion> gatherAllInclusionVersions() {
		return doGatherAllInclusionVersions(new HashSet<>());
	}

	public boolean isAnyECL() {
		return inclusionConstraints.values().stream()
				.flatMap(andConstraints -> andConstraints.constraintsFlattened().stream()).anyMatch(ConceptConstraint::hasEcl) ||
				exclusionConstraints.values().stream().flatMap(andConstraints -> andConstraints.constraintsFlattened().stream()).anyMatch(ConceptConstraint::hasEcl) ||
				nestedSelections.stream().anyMatch(CodeSelectionCriteria::isAnyECL);
	}

	public String getValueSetUserRef() {
		return valueSetUserRef;
	}

	public Map<FHIRCodeSystemVersion, AndConstraints> getInclusionConstraints() {
		return inclusionConstraints;
	}

	public Set<CodeSelectionCriteria> getNestedSelections() {
		return nestedSelections;
	}

	public Map<FHIRCodeSystemVersion, AndConstraints> getExclusionConstraints() {
		return exclusionConstraints;
	}

	private Set<FHIRCodeSystemVersion> doGatherAllInclusionVersions(Set<FHIRCodeSystemVersion> versions) {
		versions.addAll(inclusionConstraints.keySet());
		for (CodeSelectionCriteria nestedSelection : nestedSelections) {
			nestedSelection.doGatherAllInclusionVersions(versions);
		}
		return versions;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CodeSelectionCriteria that = (CodeSelectionCriteria) o;
		return Objects.equals(valueSetUserRef, that.valueSetUserRef) && Objects.equals(inclusionConstraints, that.inclusionConstraints) && Objects.equals(nestedSelections, that.nestedSelections) && Objects.equals(exclusionConstraints, that.exclusionConstraints);
	}

	@Override
	public int hashCode() {
		return Objects.hash(valueSetUserRef, inclusionConstraints, nestedSelections, exclusionConstraints);
	}
}
