package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.expression.Expression;
import org.snomed.snowstorm.core.data.services.ExpressionService;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.snomed.snowstorm.fhir.domain.FHIRDesignation;
import org.snomed.snowstorm.fhir.domain.FHIRProperty;
import org.snomed.snowstorm.fhir.pojo.ConceptAndSystemResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HapiParametersMapper implements FHIRConstants {
	
	@Autowired
	private ExpressionService expressionService;
	
	@Autowired
	private FHIRHelper fhirHelper;
	
	public Parameters mapToFHIRValidateDisplayTerm(Concept concept, String display, FHIRCodeSystemVersion codeSystemVersion) {
		Parameters parameters = new Parameters();
		if (display == null) {
			parameters.addParameter("result", true);
		} else {
			boolean valid = validateTerm(concept, display.toLowerCase(), parameters);
			parameters.addParameter("result", valid);
		}
		parameters.addParameter("display", concept.getPt().getTerm());
		parameters.addParameter("inactive", !concept.isActive());
		addSystemAndVersion(parameters, codeSystemVersion);
		return parameters;
	}
	
	public Parameters singleOutValue(String key, String value) {
		Parameters parameters = new Parameters();
		parameters.addParameter(key, value);
		return parameters;
	}

	public Parameters singleOutValue(String key, String value, FHIRCodeSystemVersion codeSystemVersion) {
		Parameters parameters = singleOutValue(key, value);
		addSystemAndVersion(parameters, codeSystemVersion);
		return parameters;
	}
	
	private boolean validateTerm(Concept c, String display, Parameters parameters) {
		//Did we get it right first time?
		if (c.getPt().getTerm().toLowerCase().equals(display)) {
			return true;
		} else {
			//TODO Implement case sensitivity checking relative to what is specified for the description
			for (Description d : c.getActiveDescriptions()) {
				if (d.getTerm().toLowerCase().equals(display)) {
					parameters.addParameter("message", "Display term is acceptable, but not the preferred synonym in the language/dialect specified.");
					return true;
				}
			}
		}
		parameters.addParameter("message", "Code exists, but the display term is not recognised.");
		return false;
	}

	public Parameters conceptNotFound(String code, FHIRCodeSystemVersion codeSystemVersion, String message) {
		Parameters parameters = new Parameters();
		parameters.addParameter("result", false);
		parameters.addParameter("code", code);
		addSystemAndVersion(parameters, codeSystemVersion);

		parameters.addParameter("message", message);
		return parameters;
	}

	public Parameters mapToFHIR(ConceptAndSystemResult conceptAndSystemResult, Collection<String> childIds,
	                            Set<FhirSctProperty> properties, List<LanguageDialect> designations) {

		FHIRCodeSystemVersion codeSystemVersion = conceptAndSystemResult.codeSystemVersion();
		Concept concept = conceptAndSystemResult.concept();

		Parameters parameters = new Parameters();
		parameters.addParameter("code", concept.getConceptId());
		parameters.addParameter("display", fhirHelper.getPreferredTerm(concept, designations));
		Optional.ofNullable(conceptAndSystemResult.codeSystemVersion().getName()).ifPresent(x->parameters.addParameter("name", x));
		//Optional.ofNullable(codeSystem.getTitle()).ifPresent(x->parameters.addParameter("title", x));
		addSystemAndVersion(parameters, conceptAndSystemResult.codeSystemVersion());
		parameters.addParameter("name", codeSystemVersion.getTitle());
		addSystemAndVersion(parameters, codeSystemVersion);
		parameters.addParameter("active", concept.isActive());
		parameters.addParameter("inactive", !concept.isActive());
		addProperties(parameters, concept, properties);
		addDesignations(parameters, concept);
		addParents(parameters, concept);
		addChildren(parameters, childIds);
		return parameters;
	}

	private void addSystemAndVersion(Parameters parameters, FHIRCodeSystemVersion codeSystem) {
		parameters.addParameter("system", codeSystem.getUrl());
		parameters.addParameter("version", codeSystem.getVersion());
	}

	public Parameters mapToFHIR(FHIRCodeSystemVersion codeSystemVersion, FHIRConcept concept) {
		Parameters parameters = new Parameters();
		parameters.addParameter("name", codeSystemVersion.getTitle());
		parameters.addParameter("system", codeSystemVersion.getUrl());
		parameters.addParameter("version", codeSystemVersion.getVersion());
		parameters.addParameter("display", concept.getDisplay());

		for (Map.Entry<String, List<FHIRProperty>> property : concept.getProperties().entrySet()) {
			for (FHIRProperty propertyValue : property.getValue()) {
				Parameters.ParametersParameterComponent param = parameters.addParameter().setName(PROPERTY);
				param.addPart().setName(CODE).setValue(new CodeType(propertyValue.getCode()));
				param.addPart().setName(VALUE).setValue(propertyValue.toHapiValue(codeSystemVersion.getUrl()));
			}
		}

		for (FHIRDesignation designation : concept.getDesignations()) {
			Parameters.ParametersParameterComponent desParam = parameters.addParameter().setName(DESIGNATION);
			if (designation.getLanguage() != null) {
				desParam.addPart().setName(LANGUAGE).setValue(new CodeType(designation.getLanguage()));
			}
			String use = designation.getUse();
			if (use != null && !use.contains("null")) {
				Type type;
				if (use.contains("|")) {
					String[] parts = use.split("\\|", 2);
					type = new Coding(parts[0], parts[1], parts[1]);
				} else {
					type = new CodeType(use);
				}
				desParam.addPart().setName(USE).setValue(type);
			}
			if (designation.getValue() != null) {
				desParam.addPart().setName(VALUE).setValue(new StringType(designation.getValue()));
			}
		}

		return parameters;
	}

	public Parameters validateCodeResponse(FHIRConcept concept, boolean displayValidOrNull, FHIRCodeSystemVersion codeSystemVersion) {
		Parameters parameters = new Parameters();
		parameters.addParameter("result", displayValidOrNull);
		parameters.addParameter("code", concept.getCode());
		addSystemAndVersion(parameters, codeSystemVersion);
		if (!displayValidOrNull) {
			parameters.addParameter("message", "The code exists but the display is not valid.");
		}
		parameters.addParameter("display", concept.getDisplay());
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
                switch (acceptability) {
                    case Concepts.ACCEPTABLE_CONSTANT ->
                            ducExt.addExtension("role", new Coding(SNOMED_URI, Concepts.ACCEPTABLE, Concepts.ACCEPTABLE_CONSTANT));
                    case Concepts.PREFERRED_CONSTANT ->
                            ducExt.addExtension("role", new Coding(SNOMED_URI, Concepts.PREFERRED, Concepts.PREFERRED_CONSTANT));
                }
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

	private void addChildren(Parameters p, Collection<String> childIds) {
		for (String childId : childIds) {
			p.addParameter(createProperty(CHILD, childId, true));
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
