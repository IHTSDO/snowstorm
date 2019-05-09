package org.snomed.snowstorm.fhir.services;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.expression.Expression;
import org.snomed.snowstorm.core.data.services.ExpressionService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.BiMap;

public class HapiParametersMapper implements FHIRConstants {
	
	@Autowired
	private ExpressionService expressionService;
	
	public Parameters mapToFHIR(Concept c, Collection<Long> childIds, Set<FhirSctProperty> properties) {
		Parameters parameters = getStandardParameters();
		Parameters.ParametersParameterComponent preferredTerm = new Parameters.ParametersParameterComponent(DISPLAY);
		parameters.addParameter(preferredTerm);
		addDesignations(parameters, c, preferredTerm);
		addProperties(parameters, c, properties);
		addParents(parameters,c);
		addChildren(parameters, childIds);
		return parameters;
	}
	
	public Parameters mapToFHIR(List<ReferenceSetMember> members, UriType requestedTargetSystem, BiMap<String, String> knownUriMap) {
		UriType targetSystem;
		Parameters p = getStandardParameters();
		boolean success = members.size() > 0;
		p.addParameter("result", success);
		if (success) {
			Parameters.ParametersParameterComponent matches = p.addParameter().setName("match");
			for (ReferenceSetMember member : members) {
				
				//Do we know about this reference set?
				String refsetId = member.getRefsetId();
				String actualTargetSystem = knownUriMap.inverse().get(refsetId);
				
				//If not, then give an indication of the refset being returned
				if (actualTargetSystem == null) {
					targetSystem = new UriType(SNOMED_URI + "?fhir_vs=ecl/^" + refsetId);
				} else {
					targetSystem = new UriType(actualTargetSystem);
				}
				
				String mapTarget = member.getAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID);
				if (mapTarget == null) {
					mapTarget = member.getAdditionalField(ReferenceSetMember.AssociationFields.MAP_TARGET);
				}
				
				if (mapTarget != null) {
					Coding coding = new Coding().setCode(mapTarget).setSystemElement(targetSystem);
					matches.addPart().setName("concept").setValue(coding);
				}
			}
		}
		return p;
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

	private void addProperties(Parameters parameters, Concept c, Set<FhirSctProperty> properties) {
		Boolean sufficientlyDefined = c.getDefinitionStatusId().equals(Concepts.SUFFICIENTLY_DEFINED);
		parameters.addParameter(createProperty(EFFECTIVE_TIME, c.getEffectiveTime(), false))
			.addParameter(createProperty(MODULE_ID, c.getModuleId(), true));
	
		if (properties.contains(FhirSctProperty.INACTVE)) {
			parameters.addParameter(createProperty(FhirSctProperty.INACTVE.toStringType(), !c.isActive(), false));
		}
		
		if (properties.contains(FhirSctProperty.SUFFICIENTLY_DEFINED)) {
			parameters.addParameter(createProperty(FhirSctProperty.SUFFICIENTLY_DEFINED.toStringType(), sufficientlyDefined, false));
		}
		
		if (properties.contains(FhirSctProperty.NORMAL_FORM_TERSE)) {
			Expression expression = expressionService.getExpression(c, false);
			parameters.addParameter(createProperty(FhirSctProperty.NORMAL_FORM_TERSE.toStringType(), expression.toString(false), false));
		}
		
		if (properties.contains(FhirSctProperty.NORMAL_FORM)) {
			Expression expression = expressionService.getExpression(c, false);
			parameters.addParameter(createProperty(FhirSctProperty.NORMAL_FORM.toStringType(), expression.toString(true), false));
		}
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
