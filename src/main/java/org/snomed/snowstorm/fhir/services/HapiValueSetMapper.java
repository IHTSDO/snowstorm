package org.snomed.snowstorm.fhir.services;

import java.util.List;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.ValueSet.ValueSetExpansionComponent;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.fhir.config.FHIRConstants;


public class HapiValueSetMapper implements FHIRConstants {
	
	public ValueSet mapToFHIR(List<ConceptMini> concepts, String url) {
		ValueSet v = getStandardValueSet(url);
		addExpansion(v, concepts);
		return v;
	}

	private ValueSet getStandardValueSet(String url) {
		ValueSet v = new ValueSet();
		v.setUrl(url);
		return v;
	}

	private void addExpansion(ValueSet v, List<ConceptMini> concepts) {
		ValueSetExpansionComponent expansion = new ValueSetExpansionComponent();
		for (ConceptMini concept : concepts) {
			expansion.addContains()
				.setCode(concept.getConceptId())
				.setDisplay(concept.getFsn())
				.setSystem(SNOMED_URI);
		}
		v.setExpansion(expansion);
	}

}
