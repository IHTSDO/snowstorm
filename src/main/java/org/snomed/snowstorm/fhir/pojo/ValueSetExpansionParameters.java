package org.snomed.snowstorm.fhir.pojo;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ValueSet;
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.snomed.snowstorm.fhir.services.FHIRValueSetProvider;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static java.lang.String.format;

public final class ValueSetExpansionParameters {

	private final String id;
	private final String url;
	private final String valueSetVersion;
	private final String context;
	private final String contextDirection;
	private final String filter;
	private final String date;
	private final Integer offset;
	private final Integer count;
	private final Boolean includeDesignations;
	private final List<String> designations;
	private final Boolean includeDefinition;
	private final Boolean activeOnly;
	private final Boolean excludeNested;
	private final Boolean excludeNotForUI;
	private final Boolean excludePostCoordinated;
	private final String displayLanguage;
	private final CanonicalUri excludeSystem;
	private final CanonicalUri systemVersion;
	private final CanonicalUri checkSystemVersion;
	private final CanonicalUri forceSystemVersion;
	private final String version;
	private final ValueSet valueSet;

	public ValueSetExpansionParameters(ValueSet valueSet, boolean includeDefinition1) {
		this(null, valueSet, null, null, null, null, null, null, null, null, null,
				null, includeDefinition1, null, null, null, null, null, null, null, null, null, null);
	}

	public ValueSetExpansionParameters(String id, ValueSet valueSet, String url, String valueSetVersion, String context, String contextDirection, String filter, String date,
			Integer offset, Integer count, Boolean includeDesignations, List<String> designations, Boolean includeDefinition, Boolean activeOnly,
			Boolean excludeNested, Boolean excludeNotForUI, Boolean excludePostCoordinated, String displayLanguage, CanonicalUri excludeSystem, CanonicalUri systemVersion,
			CanonicalUri checkSystemVersion, CanonicalUri forceSystemVersion, String version) {

		this.id = id;
		this.url = url;
		this.valueSetVersion = valueSetVersion;
		this.context = context;
		this.contextDirection = contextDirection;
		this.filter = filter;
		this.date = date;
		this.offset = offset;
		this.count = count;
		this.includeDesignations = includeDesignations;
		this.designations = designations;
		this.includeDefinition = includeDefinition;
		this.activeOnly = activeOnly;
		this.excludeNested = excludeNested;
		this.excludeNotForUI = excludeNotForUI;
		this.excludePostCoordinated = excludePostCoordinated;
		this.displayLanguage = displayLanguage;
		this.excludeSystem = excludeSystem;
		this.systemVersion = systemVersion;
		this.checkSystemVersion = checkSystemVersion;
		this.forceSystemVersion = forceSystemVersion;
		this.version = version;
		this.valueSet = valueSet;
	}

	public PageRequest getPageRequest(Sort sort) {
		int offset = this.offset != null ? this.offset : 0;
		int pageSize = this.count != null ? this.count : FHIRValueSetProvider.DEFAULT_PAGESIZE;
		if (offset % pageSize != 0) {
			throw FHIRHelper.exception(format("Parameter 'offset' '%s' must be a multiplication of 'count' (page size) '%s'.", offset, pageSize),
					OperationOutcome.IssueType.INVALID, 400);
		}
		return ControllerHelper.getPageRequest(offset, pageSize, sort);

	}

	public String getId() {
		return id;
	}

	public String getUrl() {
		return url;
	}

	public String getValueSetVersion() {
		return valueSetVersion;
	}

	public String getContext() {
		return context;
	}

	public String getContextDirection() {
		return contextDirection;
	}

	public String getFilter() {
		return filter;
	}

	public String getDate() {
		return date;
	}

	public Integer getOffset() {
		return offset;
	}

	public Integer getCount() {
		return count;
	}

	public Boolean getIncludeDesignations() {
		return includeDesignations;
	}

	public List<String> getDesignations() {
		return designations;
	}

	public Boolean getIncludeDefinition() {
		return includeDefinition;
	}

	public Boolean getActiveOnly() {
		return activeOnly;
	}

	public Boolean getExcludeNested() {
		return excludeNested;
	}

	public Boolean getExcludeNotForUI() {
		return excludeNotForUI;
	}

	public Boolean getExcludePostCoordinated() {
		return excludePostCoordinated;
	}

	public String getDisplayLanguage() {
		return displayLanguage;
	}

	public CanonicalUri getExcludeSystem() {
		return excludeSystem;
	}

	public CanonicalUri getSystemVersion() {
		return systemVersion;
	}

	public CanonicalUri getCheckSystemVersion() {
		return checkSystemVersion;
	}

	public CanonicalUri getForceSystemVersion() {
		return forceSystemVersion;
	}

	public String getVersion() {
		return version;
	}

	public ValueSet getValueSet() {
		return valueSet;
	}
}
