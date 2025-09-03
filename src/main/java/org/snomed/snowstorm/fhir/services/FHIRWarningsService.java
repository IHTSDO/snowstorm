package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.ValueSet;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.fhir.services.FHIRValueSetService.WARNING_DASH;

@Service
public class FHIRWarningsService {
	@Autowired
	private FHIRValueSetFinderService valueSetFinderService;

	public List<ValueSet.ValueSetExpansionParameterComponent> collectCodeSystemSetWarnings(Set<FHIRCodeSystemVersion> codeSystems) {
		List<ValueSet.ValueSetExpansionParameterComponent> list = new ArrayList<>();
		for (FHIRCodeSystemVersion codeSystem : codeSystems) {
			for (FHIRExtension ext : orEmpty(codeSystem.getExtensions())) {
				if (ext != null && "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status".equals(ext.getUri())) {
					list.add(new ValueSet.ValueSetExpansionParameterComponent(new StringType(WARNING_DASH + ext.getValue()))
							.setValue(new CanonicalType(codeSystem.getCanonical())));
				}
			}
			if("draft".equals(codeSystem.getStatus())) {
				list.add(new ValueSet.ValueSetExpansionParameterComponent(new StringType("warning-draft"))
						.setValue(new CanonicalType(codeSystem.getCanonical())));
			}
			if(codeSystem.isExperimental()) {
				list.add(new ValueSet.ValueSetExpansionParameterComponent(new StringType("warning-experimental"))
						.setValue(new CanonicalType(codeSystem.getCanonical())));
			}
		}
		return list;
	}

	public List<ValueSet.ValueSetExpansionParameterComponent> collectValueSetWarnings(CodeSelectionCriteria codeSelectionCriteria) {
		ArrayList<ValueSet.ValueSetExpansionParameterComponent> result = new ArrayList<>();
		collectValueSetWarnings(codeSelectionCriteria, result);
		return result;
	}

	public void collectValueSetWarnings(CodeSelectionCriteria criteria, List<ValueSet.ValueSetExpansionParameterComponent> result) {
		ValueSet valueset = valueSetFinderService.findOrInferValueSet(null, criteria.getValueSetUserRef(), null, null);
		if (valueset != null) {
			valueset.getExtension().stream()
					.filter(ext -> "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status".equals(ext.getUrl()))
					.map(warnExt ->
							new ValueSet.ValueSetExpansionParameterComponent(new StringType(WARNING_DASH + warnExt.getValue()))
									.setValue(new CanonicalType(valueset.getUrl() + "|" + valueset.getVersion())))
					.forEach(result::add);
			criteria.getNestedSelections().forEach(nestedValueSetCriteria -> collectValueSetWarnings(nestedValueSetCriteria, result));
		}
	}
}
