package org.snomed.snowstorm.fhir.services;

import java.util.Collection;
import java.util.List;

import org.hl7.fhir.r4.model.*;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.fhir.config.FHIRConstants;

public class HapiCodeSystemMapper implements FHIRConstants {
	
	public Parameters mapToFHIR(Concept c, Collection<Long> childIds) {
		Parameters parameters = getStandardParameters();
		Parameters.ParametersParameterComponent preferredTerm = new Parameters.ParametersParameterComponent(DISPLAY);
		parameters.addParameter(preferredTerm);
		addDesignations(parameters, c, preferredTerm);
		addProperties(parameters, c);
		addParents(parameters,c);
		addChildren(parameters, childIds);
		return parameters;
	}

	private Parameters getStandardParameters() {
		Parameters parameters = new Parameters();
		//String copyrightStr = COPYRIGHT.replace("YEAR", Integer.toString(Year.now().getValue()));
		//parameters.addParameter().setCopyright(copyrightStr);
		//parameters.addParameter().setName(source.getShortName());
		//parameters.addParameter().setPublisher(SNOMED_INTERNATIONAL);
		return parameters;
	}

	private void addDesignations(Parameters parameters, Concept c, Parameters.ParametersParameterComponent preferredTerm) {
		for (Description d : c.getDescriptions(true, null, null, null)) {
			Parameters.ParametersParameterComponent designation = parameters.addParameter().setName(DESIGNATION);
			designation.addPart().setName(LANGUAGE).setValue(new CodeType(d.getLang()));
			designation.addPart().setName(USE).setValue(new Coding(SNOMED_URI, d.getTypeId(), FHIRHelper.translateDescType(d.getTypeId())));
			designation.addPart().setName(VALUE).setValue(new StringType(d.getTerm()));

			//Is this the US Preferred term?
			//TODO Obtain the desired lang/dialect from request headers and lookup refsetid to use
			if (d.hasAcceptability(Concepts.PREFERRED, Concepts.US_EN_LANG_REFSET)) {
				preferredTerm.setValue(new StringType(d.getTerm()));
			}
		}
	}

	private void addProperties(Parameters parameters, Concept c) {
		Boolean sufficientlyDefined = c.getDefinitionStatusId().equals(Concepts.SUFFICIENTLY_DEFINED);
		parameters.addParameter(createProperty(EFFECTIVE_TIME, c.getEffectiveTime(), false))
			.addParameter(createProperty(MODULE_ID, c.getModuleId(), true))
			.addParameter(createProperty(SUFFICIENTLY_DEFINED, sufficientlyDefined, false));
	}

	private void addParents(Parameters p, Concept c) {
		List<Relationship> parentRels = c.getRelationships(true, Concepts.ISA, null, Concepts.INFERRED_RELATIONSHIP);
		for (Relationship thisParentRel : parentRels) {
			p.addParameter(createProperty(PARENT, thisParentRel.getDestinationId(), true));
		}
	}
	
	private void addChildren(Parameters p, Collection<Long> childIds) {
		for (Long childId : childIds) {
			p.addParameter(createProperty(CHILD, childId.toString(), true));
		}
	}

	private Parameters.ParametersParameterComponent createProperty(StringType propertyName, Object propertyValue, boolean isCode) {
		Parameters.ParametersParameterComponent property = new Parameters.ParametersParameterComponent().setName(PROPERTY);
		property.addPart().setName(CODE).setValue(propertyName);
		if (isCode) {
			property.addPart().setName(VALUE).setValue(new CodeType(propertyValue.toString()));
		} else {
			property.addPart().setName(getTypeName(propertyValue)).setValue(new StringType(propertyValue.toString()));
		}
		return property;
	}

	private String getTypeName(Object obj) {
		if (obj instanceof String) {
			return VALUE_STRING;
		} else if (obj instanceof Boolean) {
			return VALUE_BOOLEAN;
		}
		return "UNKNOWN_TYPE";
	}
}
