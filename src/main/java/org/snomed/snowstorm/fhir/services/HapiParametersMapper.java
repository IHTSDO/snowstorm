package org.snomed.snowstorm.fhir.services;

import com.google.common.collect.BiMap;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.expression.Expression;
import org.snomed.snowstorm.core.data.services.ExpressionService;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class HapiParametersMapper implements FHIRConstants {
	
	@Autowired
	private ExpressionService expressionService;
	
	@Autowired
	private FHIRHelper fhirHelper;
	
	public Parameters mapToFHIR(Concept concept, String display) {
		Parameters parameters = getStandardParameters();
		if (display == null) {
			parameters.addParameter("result", true);
		} else {
			validateTerm(concept, display.toLowerCase(), parameters);
		}
		parameters.addParameter("display", concept.getPt().getTerm());
		return parameters;
	}
	
	public Parameters singleOutValue(String key, String value) {
		Parameters parameters = getStandardParameters();
		parameters.addParameter(key, value);
		return parameters;
	}
	
	private void validateTerm(Concept c, String display, Parameters parameters) {
		//Did we get it right first time?
		if (c.getPt().getTerm().toLowerCase().equals(display)) {
			parameters.addParameter("result", true);
			return;
		} else {
			//TODO Implement case sensitivity checking relative to what is specified for the description
			for (Description d : c.getActiveDescriptions()) {
				if (d.getTerm().toLowerCase().equals(display)) {
					parameters.addParameter("result", true);
					parameters.addParameter("message", "Display term is acceptable, but not the preferred synonym in the language/dialect specified");
					return;
				}
			}
		}
		parameters.addParameter("result", false);
		parameters.addParameter("message", "Concept identifier exists, but the display term is not recognised");
		
	}

	public Parameters conceptNotFound() {
		Parameters parameters = getStandardParameters();
		parameters.addParameter("result", false);
		return parameters;
	}

	public Parameters mapToFHIR(StringType codeSystem, Concept concept, Collection<Long> childIds, Set<FhirSctProperty> properties, List<LanguageDialect> designations) {
		Parameters parameters = getStandardParameters();
		parameters.addParameter("version", codeSystem.toString());
		parameters.addParameter("display", fhirHelper.getPreferredTerm(concept, designations));
		parameters.addParameter("active", concept.isActive());
		addProperties(parameters, concept, properties);
		addDesignations(parameters, concept);
		addParents(parameters, concept);
		addChildren(parameters, childIds);
		return parameters;
	}
	
	public Parameters mapToFHIR(List<ReferenceSetMember> members, UriType requestedTargetSystem, BiMap<String, String> knownUriMap) {
		UriType targetSystem;
		Parameters p = getStandardParameters();
		boolean success = members.size() > 0;
		p.addParameter("result", success);
		boolean reverseLookup = requestedTargetSystem.asStringValue().equals(SNOMED_URI);
		
		if (success) {
			Parameters.ParametersParameterComponent matches = p.addParameter().setName("match");
			for (ReferenceSetMember member : members) {
				
				//Do we know about this reference set?
				String refsetId = member.getRefsetId();
				String actualTargetSystem = null;
				
				//Don't do lookup if we're already doing a reverse lookup
				if (!reverseLookup) {
					actualTargetSystem = knownUriMap.inverse().get(refsetId);
				}
				
				//If not, then give an indication of the refset being returned
				if (actualTargetSystem == null) {
					//targetSystem = new UriType(SNOMED_URI + "?fhir_vs=ecl/^" + refsetId);
					targetSystem = new UriType(SNOMED_URI);
				} else {
					targetSystem = new UriType(actualTargetSystem);
				}
				if (reverseLookup) {
					Coding coding = new Coding().setCode(member.getReferencedComponentId()).setSystemElement(targetSystem);
					matches.addPart().setName("concept").setValue(coding);
				} else {
					String mapTarget = member.getAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID);
					if (mapTarget == null) {
						mapTarget = member.getAdditionalField(ReferenceSetMember.AssociationFields.MAP_TARGET);
					}
					//We might be looking up an attribute value refset for an inactivation indicator MAINT-1221
					if (mapTarget == null) {
						mapTarget = member.getAdditionalField("valueId");
					}
					if (mapTarget != null) {
						Coding coding = new Coding().setCode(mapTarget).setSystemElement(targetSystem);
						matches.addPart().setName("mapTarget").setValue(coding);
					}
				}
			}
		}
		return p;
	}

	// TODO: Work out what we should be including here
	private Parameters getStandardParameters() {
		Parameters parameters = new Parameters();
		//String copyrightStr = COPYRIGHT.replace("YEAR", Integer.toString(Year.now().getValue()));
		//parameters.addParameter().setCopyright(copyrightStr);
		//parameters.addParameter().setName(source.getShortName());
		//parameters.addParameter().setPublisher(SNOMED_INTERNATIONAL);
		return parameters;
	}

	private void addDesignations(Parameters parameters, Concept c) {
		for (Description d : c.getActiveDescriptions()) {
			Parameters.ParametersParameterComponent designation = parameters.addParameter().setName(DESIGNATION);
			// 	TODO: with other values for degination.use might lead to multiple designations for the same description.
			d.getAcceptabilityMap().forEach((langRefsetId, acceptability) -> {
				Extension ducExt = new Extension("http://snomed.info/fhir/StructureDefinition/designation-use-context");
				ducExt.addExtension("context", new Coding(SNOMED_URI, langRefsetId, null)); // TODO: is there a quick way to find a description for an id? Which description? Could be in any module/branch path.
				// Add acceptability
				switch(acceptability) {
				case Concepts.ACCEPTABLE_CONSTANT:
					ducExt.addExtension("role", new Coding(SNOMED_URI, Concepts.ACCEPTABLE, Concepts.ACCEPTABLE_CONSTANT));
					break;
				case Concepts.PREFERRED_CONSTANT:
					ducExt.addExtension("role", new Coding(SNOMED_URI, Concepts.PREFERRED, Concepts.PREFERRED_CONSTANT));
				};
				// Add type, this is sometimes but not always redundant to designation.use!
				// TODO: currently it is truly redundant but as there are more alternatives for designation.use, e.g. "consumer", this is/will be needed here
				ducExt.addExtension("type", new Coding(SNOMED_URI, d.getTypeId(), FHIRHelper.translateDescType(d.getTypeId())));		

				designation.addExtension(ducExt);
			});

			designation.addPart().setName(LANGUAGE).setValue(new CodeType(d.getLang()));
			// TODO: use FHIR designation.use value set, e.g. "consumer" when an appropriate language reference set is used
			designation.addPart().setName(USE).setValue(new Coding(SNOMED_URI, d.getTypeId(), FHIRHelper.translateDescType(d.getTypeId())));
			designation.addPart().setName(VALUE).setValue(new StringType(d.getTerm()));
		}
	}

	private void addProperties(Parameters parameters, Concept c, Set<FhirSctProperty> properties) {
		Boolean sufficientlyDefined = c.getDefinitionStatusId().equals(Concepts.DEFINED);
		parameters.addParameter(createProperty(EFFECTIVE_TIME, c.getEffectiveTime(), false))
			.addParameter(createProperty(MODULE_ID, c.getModuleId(), true));
		
		boolean allProperties = properties.contains(FhirSctProperty.ALL_PROPERTIES);
	
		if (allProperties || properties.contains(FhirSctProperty.INACTVE)) {
			parameters.addParameter(createProperty(FhirSctProperty.INACTVE.toStringType(), !c.isActive(), false));
		}
		
		if (allProperties || properties.contains(FhirSctProperty.SUFFICIENTLY_DEFINED)) {
			parameters.addParameter(createProperty(FhirSctProperty.SUFFICIENTLY_DEFINED.toStringType(), sufficientlyDefined, false));
		}
		
		if (allProperties || properties.contains(FhirSctProperty.NORMAL_FORM_TERSE)) {
			Expression expression = expressionService.getExpression(c, false);
			parameters.addParameter(createProperty(FhirSctProperty.NORMAL_FORM_TERSE.toStringType(), expression.toString(false), false));
		}
		
		if (allProperties || properties.contains(FhirSctProperty.NORMAL_FORM)) {
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
		final String propertyValueString = propertyValue == null ? "" : propertyValue.toString();
		if (isCode) {
			property.addPart().setName(VALUE).setValue(new CodeType(propertyValueString));
		} else {
			StringType value = new StringType(propertyValueString);
			property.addPart().setName(getTypeName(propertyValue)).setValue(value);
		}
		return property;
	}

	private String getTypeName(Object obj) {
		if (obj instanceof String) {
			return VALUE_STRING;
		} else if (obj instanceof Boolean) {
			return VALUE_BOOLEAN;
		}
		return null;
	}
}
