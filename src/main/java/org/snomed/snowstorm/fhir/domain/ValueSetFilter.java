package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.ValueSet;
import org.fhir.ucum.TokenType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.snomed.snowstorm.fhir.services.FHIROperationException;

import ca.uhn.fhir.rest.param.InternalCodingDt;
import ca.uhn.fhir.rest.param.QuantityParam;
import ca.uhn.fhir.rest.param.StringParam;

public class ValueSetFilter {
	
	String id;
	String code;
	Coding context;
	QuantityParam contextQuantity;
	String contextType;
	String date;
	StringParam description;
	String expansion;
	StringParam identifier;
	StringParam jurisdiction;
	StringParam name;
	StringParam publisher;
	StringParam reference;
	PublicationStatus status;
	StringParam title;
	String url;
	String version;
	
	public ValueSetFilter withId(String id) throws FHIROperationException {
		if (id != null && !id.startsWith ("ValueSet/")) {
			id = "ValueSet/" + id;
		}
		this.id = id;
		return this;
	}
	public String getId() {
		return id;
	}
	public String getCode() {
		return code;
	}
	public ValueSetFilter withCode(String code) throws FHIROperationException {
		if (code != null) {
			throw new FHIROperationException(IssueType.TOOCOSTLY, "Server is unwilling to expand all known ValueSets to search for inclusion of any code");
		}
		return this;
	}
	public Coding getContext() {
		return context;
	}
	public ValueSetFilter withContext(String context) {
		//TODO Might have to split this myself.  HAPI doesn't seem to be happy with a Coding
		//says it's an unknown search parameter type
		this.context = context == null ? null : new Coding(null,
				context,
				null);
		return this;
	}
	public QuantityParam getContextQuantity() {
		return contextQuantity;
	}
	public ValueSetFilter withContextQuantity(QuantityParam contextQuantity) {
		this.contextQuantity = contextQuantity;
		if (contextQuantity != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public String getContextType() {
		return contextType;
	}
	public ValueSetFilter withContextType(String contextType) {
		this.contextType = contextType;
		if (contextType != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public String getDate() {
		return date;
	}
	public ValueSetFilter withDate(String date) {
		this.date = date;
		if (date != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public StringParam getDescription() {
		return description;
	}
	public ValueSetFilter withDescription(StringParam description) {
		this.description = description;
		return this;
	}
	public String getExpansion() {
		return expansion;
	}
	public ValueSetFilter withExpansion(String expansion) {
		this.expansion = expansion;
		if (expansion != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public StringParam getIdentifier() {
		return identifier;
	}
	public ValueSetFilter withIdentifier(StringParam identifier) {
		this.identifier = identifier;
		return this;
	}
	public StringParam getJurisdiction() {
		return jurisdiction;
	}
	public ValueSetFilter withJurisdiction(StringParam jurisdiction) {
		this.jurisdiction = jurisdiction;
		return this;
	}
	public StringParam getName() {
		return name;
	}
	public ValueSetFilter withName(StringParam name) {
		this.name = name;
		return this;
	}
	public StringParam getPublisher() {
		return publisher;
	}
	public ValueSetFilter withPublisher(StringParam publisher) {
		this.publisher = publisher;
		return this;
	}
	public StringParam getReference() {
		return reference;
	}
	public ValueSetFilter withReference(StringParam reference) {
		this.reference = reference;
		if (reference != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public PublicationStatus getStatus() {
		return status;
	}
	public ValueSetFilter withStatus(String statusStr) {
		this.status = PublicationStatus.fromCode(statusStr);
		return this;
	}
	public StringParam getTitle() {
		return title;
	}
	public ValueSetFilter withTitle(StringParam title) {
		this.title = title;
		return this;
	}
	public String getUrl() {
		return url;
	}
	public ValueSetFilter withUrl(String url) {
		this.url = url;
		return this;
	}
	public String getVersion() {
		return version;
	}
	public ValueSetFilter withVersion(String version) {
		this.version = version;
		return this;
	}
	public static boolean apply(ValueSetFilter filter, ValueSet vs, 
			QueryService queryService, FHIRHelper fhirHelper) {
		
		if (filter.getId() != null && !filter.getId().equals(vs.getId())) {
			return false;
		}
		
		if (filter.getContext() != null && !fhirHelper.hasUsageContext(vs, filter.getContext())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(vs.getDescription(), filter.getDescription()))  {
			return false;
		}
		
		if (filter.getIdentifier() != null && !fhirHelper.hasIdentifier(vs, filter.getIdentifier())) {
			return false;
		}
		
		if (filter.getJurisdiction() != null && !fhirHelper.hasJurisdiction(vs, filter.getJurisdiction())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(vs.getName(), filter.getName())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(vs.getPublisher(), filter.getPublisher())) {
			return false;
		}
		
		if (!fhirHelper.enumerationMatches (vs.getStatus(), filter.getStatus())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(vs.getTitle(), filter.getTitle())) {
			return false;
		}
		
		if (filter.getUrl() != null && !filter.getUrl().equals(vs.getUrl())) {
			return false;
		}
		
		if (filter.getVersion() != null && !filter.getVersion().equals(vs.getVersion())) {
			return false;
		}
			
		return true;
	}
	
}
