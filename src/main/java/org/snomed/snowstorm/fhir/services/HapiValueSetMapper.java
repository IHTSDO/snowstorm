package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
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
						boolean inactive = !c.isActive();
						if (inactive) {
							component.setInactive(inactive);
						}
					}
				}
			}
		}
	}


	private ConceptReferenceDesignationComponent asDesignation(Description d) {
		ConceptReferenceDesignationComponent designation = new ConceptReferenceDesignationComponent();
		designation.setLanguage(d.getLanguageCode());
		designation.setValue(d.getTerm());

		// Designation use context extension
		String descType = FHIRHelper.translateDescType(d.getTypeId());
		// For each acceptability of the acceptability map, add an extension instance
		// TODO: is there a smart way to control when the extension is intantiated?
		d.getAcceptabilityMap().forEach((langRefsetId, acceptability) -> {
			// Create designation use context Extension object using the URI
			Extension ducExt = new Extension("http://snomed.info/fhir/StructureDefinition/designation-use-context"); // TODO: are there FHIR constants anywhere?
			// Add the context, i.e. the language reference set
			ducExt.addExtension("context", new Coding(SNOMED_URI, langRefsetId, null)); // TODO: is there a quick way to find a description for an id? Which description? Could be in any module/branch path.
			// Add acceptability
			switch(acceptability) {
			case Concepts.ACCEPTABLE_CONSTANT:
				ducExt.addExtension("role", new Coding(SNOMED_URI, Concepts.ACCEPTABLE, Concepts.ACCEPTABLE_CONSTANT));
			case Concepts.PREFERRED_CONSTANT:
				ducExt.addExtension("role", new Coding(SNOMED_URI, Concepts.PREFERRED, Concepts.PREFERRED_CONSTANT));
			};
			// Add type, this is sometimes but not always redundant to designation.use!
			// TODO: currently it is truly redundant but as there are more alternatives for designation.use, e.g. "consumer", this is/will be needed here
			ducExt.addExtension("type", new Coding(SNOMED_URI, d.getTypeId(), descType));

			designation.addExtension(ducExt);
		});
		// End editing for designation use case extension
		
		Coding use = new Coding(SNOMED_URI, d.getTypeId(), descType);
		designation.setUse(use);
		return designation;
	}
}
