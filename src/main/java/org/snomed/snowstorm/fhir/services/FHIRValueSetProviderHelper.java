package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.Nullable;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.ValueSetExpansionParameters;

import java.util.List;

import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;

class FHIRValueSetProviderHelper {

	static ValueSetExpansionParameters getValueSetExpansionParameters(IdType id, final List<Parameters.ParametersParameterComponent> parametersParameterComponents) {
		Parameters.ParametersParameterComponent valueSetParam = findParameterOrNull(parametersParameterComponents, "valueSet");
		return new ValueSetExpansionParameters(
				id != null ? id.getIdPart() : null,
				valueSetParam != null ? (ValueSet) valueSetParam.getResource() : null,
				findParameterStringOrNull(parametersParameterComponents, "url"),
				findParameterStringOrNull(parametersParameterComponents, "valueSetVersion"),
				findParameterStringOrNull(parametersParameterComponents, "context"),
				findParameterStringOrNull(parametersParameterComponents, "contextDirection"),
				findParameterStringOrNull(parametersParameterComponents, "filter"),
				findParameterStringOrNull(parametersParameterComponents, "date"),
				findParameterStringOrNull(parametersParameterComponents, "offset"),
				findParameterStringOrNull(parametersParameterComponents, "count"),
				findParameterBooleanOrNull(parametersParameterComponents, "includeDesignations"),
				findParameterStringListOrNull(parametersParameterComponents, "designation"),
				findParameterBooleanOrNull(parametersParameterComponents, "includeDefinition"),
				findParameterBooleanOrNull(parametersParameterComponents, "activeOnly"),
				findParameterBooleanOrNull(parametersParameterComponents, "excludeNested"),
				findParameterBooleanOrNull(parametersParameterComponents, "excludeNotForUI"),
				findParameterBooleanOrNull(parametersParameterComponents, "excludePostCoordinated"),
				findParameterStringOrNull(parametersParameterComponents, "displayLanguage"),
				findParameterCanonicalOrNull(parametersParameterComponents, "exclude-system"),
				findParameterCanonicalOrNull(parametersParameterComponents, "system-version"),
				findParameterCanonicalOrNull(parametersParameterComponents, "check-system-version"),
				findParameterCanonicalOrNull(parametersParameterComponents, "force-system-version"),
				findParameterStringOrNull(parametersParameterComponents, "version"));
	}

	static ValueSetExpansionParameters getValueSetExpansionParameters(
			final IdType id,
			final String url,
			final String valueSetVersion,
			final String context,
			final String contextDirection,
			final String filter,
			final String date,
			final String offsetStr,
			final String countStr,
			final BooleanType includeDesignations,
			final List<String> designations,
			final BooleanType includeDefinition,
			final BooleanType activeOnly,
			final BooleanType excludeNested,
			final BooleanType excludeNotForUI,
			final BooleanType excludePostCoordinated,
			final String displayLanguage,
			final StringType excludeSystem,
			final StringType systemVersion,
			final StringType checkSystemVersion,
			final StringType forceSystemVersion,
			final StringType version) {

		return new ValueSetExpansionParameters(
				id != null ? id.getIdPart() : null,
				null,
				url,
				valueSetVersion,
				context,
				contextDirection,
				filter,
				date,
				offsetStr,
				countStr,
				getOrNull(includeDesignations),
				designations,
				getOrNull(includeDefinition),
				getOrNull(activeOnly),
				getOrNull(excludeNested),
				getOrNull(excludeNotForUI),
				getOrNull(excludePostCoordinated),
				displayLanguage,
				CanonicalUri.fromString(getOrNull(excludeSystem)),
				CanonicalUri.fromString(getOrNull(systemVersion)),
				CanonicalUri.fromString(getOrNull(checkSystemVersion)),
				CanonicalUri.fromString(getOrNull(forceSystemVersion)),
				getOrNull(version));
	}

	@Nullable
	static String getOrNull(StringType stringType) {
		return stringType != null ? stringType.toString() : null;
	}

	@Nullable
	static Boolean getOrNull(BooleanType bool) {
		return bool != null ? bool.booleanValue() : null;
	}

	static Parameters.ParametersParameterComponent findParameterOrNull(final List<Parameters.ParametersParameterComponent> parametersParameterComponents, final String name) {
		return parametersParameterComponents.stream().filter(parametersParameterComponent -> parametersParameterComponent.getName().equals(name)).findFirst().orElse(null);
	}
}
