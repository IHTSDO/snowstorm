package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.snomed.snowstorm.fhir.domain.ValueSetCycleElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.lang.String.format;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;

@Service
public class FHIRValueSetCycleDetectionService {

	@Autowired
	private FHIRValueSetFinderService vsFinderService;

	public void verifyNoCycles(ValueSet hapiValueSet) {
		List<ValueSetCycleElement> valueSetCycle = getValueSetIncludeExcludeCycle(hapiValueSet);
		if(!valueSetCycle.isEmpty()) {
			String message = getCyclicDiagnosticMessage(valueSetCycle);
			throw exception(message, OperationOutcome.IssueType.PROCESSING, 400, null, new CodeableConcept(new Coding()).setText(message));
		}
	}

	List<ValueSetCycleElement> getValueSetIncludeExcludeCycle(ValueSet valueSet) {
		return getValueSetIncludeExcludeCycle(valueSet, new HashSet<>(), true);
	}

	private List<ValueSetCycleElement> getValueSetIncludeExcludeCycle(ValueSet valueSet, Set<String> visited, boolean isIncluded) {
		if (valueSet.getCompose() == null) {
			return Collections.emptyList();
		}

		String key = valueSet.getUrl() + "|" + valueSet.getVersion();
		if (!visited.add(key)) {
			// Cycle detected
			return new ArrayList<>(List.of(new ValueSetCycleElement(isIncluded, valueSet.getUrl(), valueSet.getVersion())));
		}

		ValueSetCycleElement current = new ValueSetCycleElement(isIncluded, valueSet.getUrl(), valueSet.getVersion());

		if (valueSet.getCompose().hasInclude()) {
			var cycle = detectCycle(valueSet.getCompose().getInclude(), visited, true);
			if (!cycle.isEmpty()) {
				cycle.add(current);
				return cycle;
			}
		}

		if (valueSet.getCompose().hasExclude()) {
			var cycle = detectCycle(valueSet.getCompose().getExclude(), visited, false);
			if (!cycle.isEmpty()) {
				cycle.add(current);
				return cycle;
			}
		}

		visited.remove(key);
		return Collections.emptyList();
	}

	private List<ValueSetCycleElement> detectCycle(List<ValueSet.ConceptSetComponent> components, Set<String> visited, boolean isIncluded) {
		for (var component : components) {
			for (CanonicalType canonical : component.getValueSet()) {
				ValueSet child = vsFinderService.findOrInferValueSet(null, canonical.getValueAsString(), null, null);
				if (child != null) {
					var cycle = getValueSetIncludeExcludeCycle(child, visited, isIncluded);
					if (!cycle.isEmpty()) {
						return cycle;
					}
				}
			}
		}
		return Collections.emptyList();
	}

	public String getCyclicDiagnosticMessage(List<ValueSetCycleElement> valueSetCycle) {
		ValueSetCycleElement last = valueSetCycle.get(0);
		String lastConstraint = (last.include() ? "including " : "excluding ") + last.getCanonicalUrlVersion();
		StringBuilder parentPath = new StringBuilder();
		for(int i = 1; i < valueSetCycle.size(); i++) {
			parentPath.append(valueSetCycle.get(i).getCanonicalUrlVersion());
			if(i < valueSetCycle.size() - 1) {
				parentPath.append(", ");
			}
		}
		return format("Cyclic reference detected when %s via [%s]", lastConstraint, parentPath);
	}

}
