package org.snomed.snowstorm.fhir.pojo;

import org.hl7.fhir.r4.model.*;

import java.util.Set;

public class FHIRCodeValidationRequest {

	private String id;
	private UriType url;
	private UriType context;
	private ValueSet valueSet;
	private String valueSetVersion;
	private String code;
	private UriType system;
	private String systemVersion;
	// Canonical system-version hints (system|version) for resolving versionless valueset includes.
	// Separate from systemVersion which is the coding's bare version.
	private Set<CanonicalUri> defaultSystemVersions;
	// force-system-version: override the include version for the specified system (canonical system|version)
	private CanonicalUri forceSystemVersion;
	// check-system-version: validate that the resolved version matches the pattern (canonical system|version)
	private CanonicalUri checkSystemVersion;
	private String display;
	private Coding coding;
	private CodeableConcept codeableConcept;
	private DateTimeType date;
	private BooleanType abstractBool;
	private String displayLanguage;
	private BooleanType inferSystem;
	private BooleanType activeOnly;
	private CanonicalType versionValueSet;
	private BooleanType lenientDisplayValidation;
	private BooleanType valueSetMembershipOnly;

	public String getId() {
		return id;
	}
	public FHIRCodeValidationRequest withId(String id) {
		this.id = id;
		return this;
	}
	public UriType getUrl() {
		return url;
	}
	public FHIRCodeValidationRequest withUrl(UriType url) {
		this.url = url;
		return this;
	}
	public UriType getContext() {
		return context;
	}
	public FHIRCodeValidationRequest withContext(UriType context) {
		this.context = context;
		return this;
	}
	public ValueSet getValueSet() {
		return valueSet;
	}
	public FHIRCodeValidationRequest withValueSet(ValueSet valueSet) {
		this.valueSet = valueSet;
		return this;
	}
	public String getValueSetVersion() {
		return valueSetVersion;
	}
	public FHIRCodeValidationRequest withValueSetVersion(String valueSetVersion) {
		this.valueSetVersion = valueSetVersion;
		return this;
	}
	public String getCode() {
		return code;
	}
	public FHIRCodeValidationRequest withCode(String code) {
		this.code = code;
		return this;
	}
	public UriType getSystem() {
		return system;
	}
	public FHIRCodeValidationRequest withSystem(UriType system) {
		this.system = system;
		return this;
	}
	public String getSystemVersion() {
		return systemVersion;
	}
	public FHIRCodeValidationRequest withSystemVersion(String systemVersion) {
		this.systemVersion = systemVersion;
		return this;
	}
	public Set<CanonicalUri> getDefaultSystemVersions() {
		return defaultSystemVersions;
	}
	public FHIRCodeValidationRequest withDefaultSystemVersions(Set<CanonicalUri> defaultSystemVersions) {
		this.defaultSystemVersions = defaultSystemVersions;
		return this;
	}
	public String getDisplay() {
		return display;
	}
	public FHIRCodeValidationRequest withDisplay(String display) {
		this.display = display;
		return this;
	}
	public Coding getCoding() {
		return coding;
	}
	public FHIRCodeValidationRequest withCoding(Coding coding) {
		this.coding = coding;
		return this;
	}
	public CodeableConcept getCodeableConcept() {
		return codeableConcept;
	}
	public FHIRCodeValidationRequest withCodeableConcept(CodeableConcept codeableConcept) {
		this.codeableConcept = codeableConcept;
		return this;
	}
	public DateTimeType getDate() {
		return date;
	}
	public FHIRCodeValidationRequest withDate(DateTimeType date) {
		this.date = date;
		return this;
	}
	public BooleanType getAbstractBool() {
		return abstractBool;
	}
	public FHIRCodeValidationRequest withAbstractBool(BooleanType abstractBool) {
		this.abstractBool = abstractBool;
		return this;
	}
	public String getDisplayLanguage() {
		return displayLanguage;
	}
	public FHIRCodeValidationRequest withDisplayLanguage(String displayLanguage) {
		this.displayLanguage = displayLanguage;
		return this;
	}
	public void setDisplayLanguage(String displayLanguage) {
		this.displayLanguage = displayLanguage;
	}
	public BooleanType getInferSystem() {
		return inferSystem;
	}
	public FHIRCodeValidationRequest withInferSystem(BooleanType inferSystem) {
		this.inferSystem = inferSystem;
		return this;
	}
	public BooleanType getActiveOnly() {
		return activeOnly;
	}
	public FHIRCodeValidationRequest withActiveOnly(BooleanType activeOnly) {
		this.activeOnly = activeOnly;
		return this;
	}
	public CanonicalType getVersionValueSet() {
		return versionValueSet;
	}
	public FHIRCodeValidationRequest withVersionValueSet(CanonicalType versionValueSet) {
		this.versionValueSet = versionValueSet;
		return this;
	}
	public BooleanType getLenientDisplayValidation() {
		return lenientDisplayValidation;
	}
	public FHIRCodeValidationRequest withLenientDisplayValidation(BooleanType lenientDisplayValidation) {
		this.lenientDisplayValidation = lenientDisplayValidation;
		return this;
	}
	public BooleanType getValueSetMembershipOnly() {
		return valueSetMembershipOnly;
	}
	public FHIRCodeValidationRequest withValueSetMembershipOnly(BooleanType valueSetMembershipOnly) {
		this.valueSetMembershipOnly = valueSetMembershipOnly;
		return this;
	}
	public CanonicalUri getForceSystemVersion() {
		return forceSystemVersion;
	}
	public FHIRCodeValidationRequest withForceSystemVersion(CanonicalUri forceSystemVersion) {
		this.forceSystemVersion = forceSystemVersion;
		return this;
	}
	public CanonicalUri getCheckSystemVersion() {
		return checkSystemVersion;
	}
	public FHIRCodeValidationRequest withCheckSystemVersion(CanonicalUri checkSystemVersion) {
		this.checkSystemVersion = checkSystemVersion;
		return this;
	}
}
