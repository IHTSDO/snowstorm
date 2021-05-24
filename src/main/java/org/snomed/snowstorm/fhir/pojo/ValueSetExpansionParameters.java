package org.snomed.snowstorm.fhir.pojo;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.fhir.domain.BranchPath;
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.snomed.snowstorm.fhir.services.FHIRValueSetProvider;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.ValueSet;

import java.util.List;
import java.util.Objects;

public final class ValueSetExpansionParameters {

	private final String url;
	private final String filter;
	private final BooleanType activeType;
	private final BooleanType includeDesignationsType;
	private final List<String> designations;
	private final String displayLanguage;
	private final String offsetStr;
	private final String countStr;
	private final StringType systemVersion;
	private final StringType forceSystemVersion;
	private final ValueSet valueSet;

	private ValueSetExpansionParameters(final String url, final String filter, final BooleanType activeType, final BooleanType includeDesignationsType,
			final List<String> designations, final String displayLanguage, final String offsetStr, final String countStr,
			final StringType systemVersion, final StringType forceSystemVersion, final ValueSet valueSet) {
		this.url = url;
		this.filter = filter;
		this.activeType = activeType;
		this.includeDesignationsType = includeDesignationsType;
		this.designations = designations;
		this.displayLanguage = displayLanguage;
		this.offsetStr = offsetStr;
		this.countStr = countStr;
		this.systemVersion = systemVersion;
		this.forceSystemVersion = forceSystemVersion;
		this.valueSet = valueSet;
	}

	public final String getUrl() {
		return url;
	}

	public final String getFilter() {
		return filter;
	}

	public final BooleanType getActiveType() {
		return activeType;
	}

	public final BooleanType getIncludeDesignationsType() {
		return includeDesignationsType;
	}

	public final List<String> getDesignations() {
		return designations;
	}

	public final String getDisplayLanguage() {
		return displayLanguage;
	}

	public final String getOffsetStr() {
		return offsetStr;
	}

	public final String getCountStr() {
		return countStr;
	}
	
	public final PageRequest getPageRequest() {
		int offset = (offsetStr == null || offsetStr.isEmpty()) ? 0 : Integer.parseInt(offsetStr);
		int pageSize = (countStr == null || countStr.isEmpty()) ? FHIRValueSetProvider.DEFAULT_PAGESIZE : Integer.parseInt(countStr);
		return ControllerHelper.getPageRequest(offset, pageSize, FHIRHelper.DEFAULT_SORT);

	}

	public final StringType getSystemVersion() {
		return systemVersion;
	}

	public final StringType getForceSystemVersion() {
		return forceSystemVersion;
	}

	public final ValueSet getValueSet() {
		return valueSet;
	}

	public static BuilderFromPOST newBuilderFromPOST() {
		return new BuilderFromPOST();
	}

	public static BuilderFromGET newBuilderFromGET() {
		return new BuilderFromGET();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ValueSetExpansionParameters that = (ValueSetExpansionParameters) o;
		return Objects.equals(url, that.url) &&
				Objects.equals(filter, that.filter) &&
				Objects.equals(activeType, that.activeType) &&
				Objects.equals(includeDesignationsType, that.includeDesignationsType) &&
				Objects.equals(designations, that.designations) &&
				Objects.equals(displayLanguage, that.displayLanguage) &&
				Objects.equals(offsetStr, that.offsetStr) &&
				Objects.equals(countStr, that.countStr) &&
				Objects.equals(systemVersion, that.systemVersion) &&
				Objects.equals(forceSystemVersion, that.forceSystemVersion) &&
				Objects.equals(valueSet, that.valueSet);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, filter, activeType, includeDesignationsType, designations, displayLanguage,
				offsetStr, countStr, systemVersion, forceSystemVersion, valueSet);
	}

	public static class BuilderFromGET {

		private String url;
		private String filter;
		private BooleanType activeType;
		private BooleanType includeDesignationsType;
		private List<String> designations;
		private String displayLanguage;
		private String offsetStr;
		private String countStr;
		private StringType systemVersion;
		private StringType forceSystemVersion;

		public final BuilderFromGET withUrl(final String url) {
			this.url = url;
			return this;
		}

		public final BuilderFromGET withFilter(final String filter) {
			this.filter = filter;
			return this;
		}

		public final BuilderFromGET withActiveType(final BooleanType activeType) {
			this.activeType = activeType;
			return this;
		}

		public final BuilderFromGET withIncludeDesignationsType(final BooleanType includeDesignationsType) {
			this.includeDesignationsType = includeDesignationsType;
			return this;
		}

		public final BuilderFromGET withDesignations(final List<String> designations) {
			this.designations = designations;
			return this;
		}

		public final BuilderFromGET withDisplayLanguage(final String displayLanguage) {
			this.displayLanguage = displayLanguage;
			return this;
		}

		public final BuilderFromGET withOffset(final String offsetStr) {
			this.offsetStr = offsetStr;
			return this;
		}

		public final BuilderFromGET withCount(final String countStr) {
			this.countStr = countStr;
			return this;
		}

		public final BuilderFromGET withSystemVersion(final StringType systemVersion) {
			this.systemVersion = systemVersion;
			return this;
		}

		public final BuilderFromGET withForceSystemVersion(final StringType forceSystemVersion) {
			this.forceSystemVersion = forceSystemVersion;
			return this;
		}

		@SuppressWarnings("unchecked")
		public final ValueSetExpansionParameters build() {
			return new ValueSetExpansionParameters(url == null ? null : url,
					filter == null ? null : filter,
					activeType == null ? null : activeType,
					includeDesignationsType == null ? null : includeDesignationsType,
					designations == null ? null : designations,
					displayLanguage == null ? null : displayLanguage,
					offsetStr == null ? null : offsetStr,
					countStr == null ? null : countStr,
					systemVersion == null ? null : systemVersion,
					forceSystemVersion == null ? null : forceSystemVersion,
					null);
		}
	}

	public static class BuilderFromPOST {

		private ParametersParameterComponent url;
		private ParametersParameterComponent filter;
		private ParametersParameterComponent activeType;
		private ParametersParameterComponent includeDesignationsType;
		private ParametersParameterComponent designations;
		private ParametersParameterComponent displayLanguage;
		private ParametersParameterComponent offsetStr;
		private ParametersParameterComponent countStr;
		private ParametersParameterComponent systemVersion;
		private ParametersParameterComponent forceSystemVersion;
		private ParametersParameterComponent valueSet;

		public final BuilderFromPOST withUrl(final ParametersParameterComponent url) {
			this.url = url;
			return this;
		}

		public final BuilderFromPOST withFilter(final ParametersParameterComponent filter) {
			this.filter = filter;
			return this;
		}

		public final BuilderFromPOST withActiveType(final ParametersParameterComponent activeType) {
			this.activeType = activeType;
			return this;
		}

		public final BuilderFromPOST withIncludeDesignationsType(final ParametersParameterComponent includeDesignationsType) {
			this.includeDesignationsType = includeDesignationsType;
			return this;
		}

		public final BuilderFromPOST withDesignations(final ParametersParameterComponent designations) {
			this.designations = designations;
			return this;
		}

		public final BuilderFromPOST withDisplayLanguage(final ParametersParameterComponent displayLanguage) {
			this.displayLanguage = displayLanguage;
			return this;
		}

		public final BuilderFromPOST withOffset(final ParametersParameterComponent offsetStr) {
			this.offsetStr = offsetStr;
			return this;
		}

		public final BuilderFromPOST withCount(final ParametersParameterComponent countStr) {
			this.countStr = countStr;
			return this;
		}

		public final BuilderFromPOST withSystemVersion(final ParametersParameterComponent systemVersion) {
			this.systemVersion = systemVersion;
			return this;
		}

		public final BuilderFromPOST withForceSystemVersion(final ParametersParameterComponent forceSystemVersion) {
			this.forceSystemVersion = forceSystemVersion;
			return this;
		}

		public final BuilderFromPOST withValueSet(final ParametersParameterComponent valueSet) {
			this.valueSet = valueSet;
			return this;
		}

		@SuppressWarnings("unchecked")
		public final ValueSetExpansionParameters build() {
			return new ValueSetExpansionParameters(url == null ? null : String.valueOf(url.getValue()),
					filter == null ? null : String.valueOf(filter.getValue()),
					activeType == null ? null : (BooleanType) activeType.getValue(),
					includeDesignationsType == null ? null : (BooleanType) includeDesignationsType.getValue(),
					designations == null ? null : (List<String>) designations.getValue(),
					displayLanguage == null ? null : String.valueOf(displayLanguage.getValue()),
					offsetStr == null ? null : String.valueOf(offsetStr.getValue()),
					countStr == null ? null : String.valueOf(countStr.getValue()),
					systemVersion == null ? null : (StringType) systemVersion.getValue(),
					forceSystemVersion == null ? null : (StringType) forceSystemVersion.getValue(),
					valueSet == null ? null : (ValueSet) valueSet.getResource());
		}
	}
}
