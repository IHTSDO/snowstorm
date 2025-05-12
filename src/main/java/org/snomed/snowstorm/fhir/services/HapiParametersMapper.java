package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.Identifier;
import org.snomed.snowstorm.core.data.domain.expression.Expression;
import org.snomed.snowstorm.core.data.services.CodeSystemDefaultConfigurationService;
import org.snomed.snowstorm.core.data.services.ExpressionService;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemDefaultConfiguration;
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
	@Autowired
	private CodeSystemDefaultConfigurationService codeSystemDefaultConfigurationService;

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

	public Parameters resultFalseWithMessage(String code, FHIRCodeSystemVersion codeSystemVersion, String message) {
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
		boolean postcoordinated = conceptAndSystemResult.postcoordinated();

		Parameters parameters = new Parameters();
		parameters.addParameter("code", concept.getConceptId());
		parameters.addParameter("display", fhirHelper.getPreferredTerm(concept, designations));
		Optional.ofNullable(conceptAndSystemResult.codeSystemVersion().getName()).ifPresent(x->parameters.addParameter("name", x));
		//Optional.ofNullable(codeSystem.getTitle()).ifPresent(x->parameters.addParameter("title", x));
		addSystemAndVersion(parameters, conceptAndSystemResult.codeSystemVersion());
		boolean postcoordinatedCodeOnStandardCodeSystem = postcoordinated && !codeSystemVersion.getSnomedCodeSystem().isPostcoordinatedNullSafe();
		parameters.addParameter("name", codeSystemVersion.getTitle() + (postcoordinatedCodeOnStandardCodeSystem ? " (Postcoordinated)" : ""));
		addSystemAndVersion(parameters, codeSystemVersion);
		parameters.addParameter("active", concept.isActive());
		parameters.addParameter("inactive", !concept.isActive());
		if (!postcoordinated) {
			addProperties(parameters, concept, properties);
			addDesignations(parameters, concept);
			addParents(parameters, concept);
			addChildren(parameters, childIds);
			addIdentifiers(parameters, concept);
		}
		return parameters;
	}

	private void addSystemAndVersion(Parameters parameters, FHIRCodeSystemVersion codeSystem) {
		parameters.addParameter("system", codeSystem.getUrl());
		parameters.addParameter("version", codeSystem.getVersion());
	}

	public Parameters mapToFHIR(FHIRCodeSystemVersion codeSystemVersion, FHIRConcept concept) {
		Parameters parameters = new Parameters();
		Optional.ofNullable(codeSystemVersion.getName()).ifPresent(x->parameters.addParameter("name", x));
		//Optional.of(codeSystemVersion.getTitle()).ifPresent(x->parameters.addParameter("title", x));
		parameters.addParameter("system", new UriType(codeSystemVersion.getUrl()));
		parameters.addParameter("version", codeSystemVersion.getVersion());
		parameters.addParameter("display", concept.getDisplay());
		parameters.addParameter("code", new CodeType(concept.getCode()));

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
			// 	TODO: with other values for designation.use might lead to multiple designations for the same description.
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

	private void addIdentifiers(Parameters parameters, Concept c) {
		for (Identifier identifier: c.getIdentifiers()) {
			//We're going to need to look up the URI for this schema, supplied via configuration until we can add these
			//as non-definining attributes to the schema concepts
			CodeSystemDefaultConfiguration codeSystem = codeSystemDefaultConfigurationService.findByAlternativeSchemaSctid(identifier.getIdentifierSchemaId());
			String alternateSchemaUri = codeSystem != null ? codeSystem.alternateSchemaUri() : null;
			Coding coding = new Coding(alternateSchemaUri, identifier.getAlternateIdentifier(), null);
			parameters.addParameter(createProperty(EQUIVALENT_CONCEPT, coding, FHIRProperty.CODING_TYPE));
		}
	}

	private void addProperties(Parameters parameters, Concept c, Set<FhirSctProperty> properties) {
		Boolean sufficientlyDefined = c.getDefinitionStatusId().equals(Concepts.DEFINED);

		if (c.getEffectiveTime() != null) {
			parameters.addParameter(createProperty(EFFECTIVE_TIME, c.getEffectiveTime(), FHIRProperty.STRING_TYPE));
		}
		if (c.getModuleId() != null) {
			parameters.addParameter(createProperty(MODULE_ID, c.getModuleId(), FHIRProperty.CODE_TYPE));
		}

		boolean allProperties = properties.contains(FhirSctProperty.ALL_PROPERTIES);

		if (allProperties || properties.contains(FhirSctProperty.INACTVE)) {
			parameters.addParameter(createProperty(FhirSctProperty.INACTVE.toStringType(), !c.isActive(), FHIRProperty.BOOLEAN_TYPE));
		}

		if (allProperties || properties.contains(FhirSctProperty.SUFFICIENTLY_DEFINED)) {
			parameters.addParameter(createProperty(FhirSctProperty.SUFFICIENTLY_DEFINED.toStringType(), sufficientlyDefined, FHIRProperty.BOOLEAN_TYPE));
		}

		if (allProperties || properties.contains(FhirSctProperty.NORMAL_FORM_TERSE)) {
			Expression expression = expressionService.getExpression(c, false);
			parameters.addParameter(createProperty(FhirSctProperty.NORMAL_FORM_TERSE.toStringType(), expression.toString(false), FHIRProperty.STRING_TYPE));
		}

		if (allProperties || properties.contains(FhirSctProperty.NORMAL_FORM)) {
			Expression expression = expressionService.getExpression(c, false);
			parameters.addParameter(createProperty(FhirSctProperty.NORMAL_FORM.toStringType(), expression.toString(true), FHIRProperty.STRING_TYPE));
		}
	}

	private void addParents(Parameters parameters, Concept c) {
		List<Relationship> parentRels = c.getRelationships(true, Concepts.ISA, null, Concepts.INFERRED_RELATIONSHIP);
		for (Relationship thisParentRel : parentRels) {
			parameters.addParameter(createProperty(PARENT, thisParentRel.getDestinationId(), FHIRProperty.CODE_TYPE));
		}
	}

	private void addChildren(Parameters parameters, Collection<String> childIds) {
		for (String childId : childIds) {
			parameters.addParameter(createProperty(CHILD, childId, FHIRProperty.CODE_TYPE));
		}
	}

	private Parameters.ParametersParameterComponent createProperty(StringType propertyName, Object propertyValue, String propertyType) {
		Parameters.ParametersParameterComponent property = new Parameters.ParametersParameterComponent().setName(PROPERTY);
		property.addPart().setName(CODE).setValue(propertyName);
		final String propertyValueString = propertyValue == null ? "" : propertyValue.toString();
		switch (propertyType) {
			case FHIRProperty.CODE_TYPE:
				property.addPart().setName(VALUE).setValue(new CodeType(propertyValueString));
				break;
			case FHIRProperty.CODING_TYPE:
				if (propertyValue instanceof Coding coding) {
					property.addPart().setName(VALUE).setValue(coding);
				} else {
					throw new IllegalArgumentException(propertyValue + " is not of type 'Coding'");
				}
				break;
			default:
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
