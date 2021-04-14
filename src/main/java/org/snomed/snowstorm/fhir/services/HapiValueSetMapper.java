package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceDesignationComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;

import java.util.List;
import java.util.Map;

public class HapiValueSetMapper implements FHIRConstants {
	
	public ValueSet mapToFHIR(ValueSet vs, List<ConceptMini> concepts, String url, Map<String, Concept> conceptDetails, List<LanguageDialect> designations, Boolean includeDesignations) {
		if (vs == null) {
			vs = getStandardValueSet(url);
		}
		addExpansion(vs, concepts, conceptDetails, designations, includeDesignations);
		return vs;
	}
	
	private ValueSet getStandardValueSet(String url) {
		ValueSet v = new ValueSet();
		v.setUrl(url);
		return v;
	}

	private void addExpansion(ValueSet vs, List<ConceptMini> concepts, Map<String, Concept> conceptDetails, List<LanguageDialect> designations, Boolean includeDesignations) {
		ValueSetExpansionComponent expansion = vs.getExpansion();  //Will autocreate
		for (ConceptMini concept : concepts) {
			ValueSetExpansionContainsComponent component = expansion.addContains()
				.setCode(concept.getConceptId())
				.setSystem(SNOMED_URI);
			
			if (conceptDetails != null && conceptDetails.containsKey(concept.getConceptId())) {
				Concept c = conceptDetails.get(concept.getConceptId());
				for (Description d : c.getActiveDescriptions()) {
					if (includeDesignations && d.hasAcceptability(designations)) {
						component.addDesignation(asDesignation(d));
					}
					
					//Use the preferred term in the specified display language.
					if (!designations.isEmpty() && d.hasAcceptability(Concepts.PREFERRED, designations.get(0)) &&
							d.getTypeId().equals(Concepts.SYNONYM)) {
						component.setDisplay(d.getTerm());
						component.setInactive(!c.isActive());
					}
				}
			}
		}
	}


	private ConceptReferenceDesignationComponent asDesignation(Description d) {
		ConceptReferenceDesignationComponent designation = new ConceptReferenceDesignationComponent();
		designation.setLanguage(d.getLanguageCode());
		designation.setValue(d.getTerm());
		Coding use = new Coding(SNOMED_URI, d.getTypeId(), FHIRHelper.translateDescType(d.getTypeId()));
		designation.setUse(use);
		return designation;
	}
}
